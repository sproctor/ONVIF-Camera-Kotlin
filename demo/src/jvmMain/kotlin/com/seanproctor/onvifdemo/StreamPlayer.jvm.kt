package com.seanproctor.onvifdemo

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.*
import org.bytedeco.ffmpeg.global.avformat.*
import org.bytedeco.ffmpeg.global.avutil.*
import org.bytedeco.ffmpeg.global.swscale.*
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.DoublePointer
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

@Composable
actual fun StreamPlayer(url: String, modifier: Modifier) {
    var currentFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(url) {
        isLoading = true
        error = null

        withContext(Dispatchers.IO) {
            try {
                streamRTSP(url) { bitmap ->
                    currentFrame = bitmap
                    isLoading = false
                }
            } catch (e: Exception) {
                error = "Stream error: ${e.message}"
                isLoading = false
            }
        }
    }

    when {
        isLoading -> {
            Box(modifier = modifier) {
                CircularProgressIndicator()
            }
        }

        error != null -> {
            Box(modifier = modifier) {
                Text("Error: $error")
            }
        }

        currentFrame != null -> {
            Image(
                bitmap = currentFrame!!,
                contentDescription = "RTSP Stream",
                modifier = modifier
            )
        }
    }
}

private suspend fun streamRTSP(
    url: String,
    onFrame: (ImageBitmap) -> Unit
) = withContext(Dispatchers.IO) {
    avformat_network_init()

    val formatContext = avformat_alloc_context()

    // Set options for RTSP
    val options = AVDictionary()
    av_dict_set(options, "rtsp_transport", "tcp", 0)
    av_dict_set(options, "max_delay", "500000", 0)

    // Open input stream
    if (avformat_open_input(formatContext, url, null, options) < 0) {
        throw RuntimeException("Could not open RTSP stream")
    }

    // Retrieve stream information
    if (avformat_find_stream_info(formatContext, null as AVDictionary?) < 0) {
        avformat_close_input(formatContext)
        throw RuntimeException("Could not find stream information")
    }

    // Find the video stream
    var videoStreamIndex = -1
    for (i in 0 until formatContext.nb_streams()) {
        val stream = formatContext.streams(i)
        if (stream.codecpar().codec_type() == AVMEDIA_TYPE_VIDEO) {
            videoStreamIndex = i
            break
        }
    }

    if (videoStreamIndex == -1) {
        avformat_close_input(formatContext)
        throw RuntimeException("Could not find video stream")
    }

    val videoStream = formatContext.streams(videoStreamIndex)
    val codecParams = videoStream.codecpar()

    // Find decoder
    val codec = avcodec_find_decoder(codecParams.codec_id())
    if (codec == null) {
        avformat_close_input(formatContext)
        throw RuntimeException("Unsupported codec")
    }

    // Allocate codec context
    val codecContext = avcodec_alloc_context3(codec)
    if (avcodec_parameters_to_context(codecContext, codecParams) < 0) {
        avcodec_free_context(codecContext)
        avformat_close_input(formatContext)
        throw RuntimeException("Could not copy codec parameters")
    }

    // Open codec
    if (avcodec_open2(codecContext, codec, null as AVDictionary?) < 0) {
        avcodec_free_context(codecContext)
        avformat_close_input(formatContext)
        throw RuntimeException("Could not open codec")
    }

    val frame = av_frame_alloc()
    val packet = av_packet_alloc()

    // Setup scaler for RGB conversion
    val swsContext = sws_getContext(
        codecContext.width(), codecContext.height(), codecContext.pix_fmt(),
        codecContext.width(), codecContext.height(), AV_PIX_FMT_RGBA,
        SWS_BILINEAR, null, null, null as DoublePointer?
    )

    val rgbFrame = av_frame_alloc()
    rgbFrame.format(AV_PIX_FMT_RGBA)
    rgbFrame.width(codecContext.width())
    rgbFrame.height(codecContext.height())

    av_frame_get_buffer(rgbFrame, 32)

    try {
        while (isActive) {
            // Read frame
            if (av_read_frame(formatContext, packet) < 0) {
                println("Error reading frame")
                break
            }

            if (packet.stream_index() == videoStreamIndex) {
                // Send packet to decoder
                if (avcodec_send_packet(codecContext, packet) >= 0) {
                    // Receive decoded frame
                    while (avcodec_receive_frame(codecContext, frame) >= 0) {
                        // Convert to RGBA
                        sws_scale(
                            swsContext,
                            frame.data(),
                            frame.linesize(),
                            0,
                            codecContext.height(),
                            rgbFrame.data(),
                            rgbFrame.linesize()
                        )

                        // Convert to ImageBitmap
                        val bitmap = frameToImageBitmap(rgbFrame, codecContext.width(), codecContext.height())
                        withContext(Dispatchers.Main) {
                            onFrame(bitmap)
                        }
                    }
                }
            }

            av_packet_unref(packet)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        // Cleanup
        av_frame_free(rgbFrame)
        av_frame_free(frame)
        av_packet_free(packet)
        sws_freeContext(swsContext)
        avcodec_free_context(codecContext)
        avformat_close_input(formatContext)
        avformat_network_deinit()
    }
}

private fun frameToImageBitmap(frame: AVFrame, width: Int, height: Int): ImageBitmap {
    val bufferSize = width * height * 4 // RGBA
    val buffer = ByteArray(bufferSize)

    val data = frame.data(0)
    val linesize = frame.linesize(0)

    // Copy frame data to buffer
    var bufferOffset = 0
    for (y in 0 until height) {
        val linePointer = BytePointer(data).position((y * linesize).toLong())
        linePointer.get(buffer, bufferOffset, width * 4)
        bufferOffset += width * 4
    }

    val imageInfo = ImageInfo(
        width = width,
        height = height,
        colorType = ColorType.RGBA_8888,
        alphaType = ColorAlphaType.OPAQUE
    )

    return org.jetbrains.skia.Image.makeRaster(
        imageInfo,
        buffer,
        width * 4
    ).toComposeImageBitmap()
}

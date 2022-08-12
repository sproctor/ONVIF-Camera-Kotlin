package com.seanproctor.onvifdemo

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.seanproctor.onvifcamera.customDigest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SnapshotActivity : ComponentActivity() {

    private val tag = "SnapshotActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_snapshot)

        val imageView = findViewById<ImageView>(R.id.image_view)

        val username = intent.getStringExtra(USERNAME)
        val password = intent.getStringExtra(PASSWORD)
        val url = intent.getStringExtra(JPEG_URL)!!

        // TODO: move this to a ViewModel
        HttpClient {
            if (username != null && password != null) {
                install(Auth) {
                    basic {
                        credentials {
                            BasicAuthCredentials(username = username, password = password)
                        }
                    }
                    customDigest {
                        credentials {
                            DigestAuthCredentials(username = username, password = password)
                        }
                    }
                }
            }
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.BODY
            }
        }.use { client ->
            lifecycleScope.launch(Dispatchers.IO) {
                Log.d(tag, "Getting snapshot: $url")
                val response = client.get(url)
                if (response.status.value in 200..299) {
                    val data: ByteArray = response.body()
                    val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                    runOnUiThread {
                        imageView.setImageBitmap(bitmap)
                    }
                } else {
                    Toast.makeText(
                        this@SnapshotActivity,
                        response.status.toString(),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
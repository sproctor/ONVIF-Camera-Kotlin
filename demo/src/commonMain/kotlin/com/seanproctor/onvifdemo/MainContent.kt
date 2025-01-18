package com.seanproctor.onvifdemo

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.ktor.http.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(viewModel: MainViewModel) {
    var screen: Screen by remember { mutableStateOf(Screen.Main) }
    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            when (screen) {
                Screen.Main -> TopAppBar(title = { Text("ONVIF Camera Demo") })
                is Screen.CameraConnect ->
                    TopAppBar(
                        title = { Text("Camera Connect") },
                        navigationIcon = {
                            IconButton(onClick = { screen = Screen.Main }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    )

                is Screen.CameraDetails ->
                    TopAppBar(
                        title = { Text("Camera Details") },
                        navigationIcon = {
                            IconButton(onClick = { screen = Screen.Main }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close"
                                )
                            }
                        }
                    )

                is Screen.Snapshot ->
                    TopAppBar(
                        title = { Text("Snapshot") },
                        navigationIcon = {
                            IconButton(onClick = { screen = Screen.Main }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close"
                                )
                            }
                        }
                    )

                is Screen.Stream ->
                    TopAppBar(
                        title = { Text("Stream") },
                        navigationIcon = {
                            IconButton(onClick = { screen = Screen.Main }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close"
                                )
                            }
                        }
                    )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (val currentScreen = screen) {
                Screen.Main -> CameraListContent(
                    viewModel = viewModel,
                    onClick = { screen = Screen.CameraConnect(it) })

                is Screen.CameraConnect -> CameraConnectContent(
                    viewModel = viewModel,
                    cameraInfo = currentScreen.camera,
                    onConnect = {
                        viewModel.connectClicked()
                        screen = Screen.CameraDetails
                    }
                )

                is Screen.CameraDetails ->
                    CameraDetailsContent(
                        viewModel = viewModel,
                        onCapture = {
                            viewModel.getSnapshot()
                            screen = Screen.Snapshot
                        },
                        onStream = {
                            screen = Screen.Stream
                        }
                    )

                is Screen.Snapshot -> SnapshotContent(viewModel)

                is Screen.Stream -> StreamContent(viewModel)
            }
        }
    }

    val errorText = viewModel.errorText.collectAsState().value
    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbarHostState.showSnackbar(
                message = errorText,
                actionLabel = "Dismiss",
                duration = SnackbarDuration.Long
            )
            viewModel.clearErrorText()
        }
    }
}

@Composable
fun CameraListContent(
    viewModel: MainViewModel,
    onClick: (CameraInformation) -> Unit,
) {
    var scanning by remember { mutableStateOf(false) }
    var discoveredDevices: List<CameraInformation> by remember { mutableStateOf(emptyList()) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(
                items = discoveredDevices,
                key = { it.id },
            ) {
                ListItem(
                    modifier = Modifier.clickable {
                        scanning = false
                        onClick(it)
                    },
                    headlineContent = { Text(it.friendlyName ?: it.id) },
                    supportingContent = { Text(it.host) }
                )
            }
            if (discoveredDevices.isEmpty()) {
                item {
                    Text("No devices found")
                }
            }
        }
        if (scanning) {
            Button(onClick = { scanning = false }) {
                Text("Cancel")
            }
        } else {
            Button(onClick = { scanning = true }) {
                Text("Scan")
            }
        }
    }
    LaunchedEffect(scanning) {
        if (scanning) {
            viewModel.discoverDevices()
                .collect {
                    discoveredDevices = it
                }
        }
    }
}

@Composable
fun CameraConnectContent(
    viewModel: MainViewModel,
    cameraInfo: CameraInformation?,
    onConnect: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextField(
            value = viewModel.address,
            onValueChange = { viewModel.address = it },
            label = { Text("Address") },
            singleLine = true,
        )

        TextField(
            value = viewModel.username,
            onValueChange = { viewModel.username = it },
            label = { Text("Username") },
            singleLine = true,
        )

        TextField(
            value = viewModel.password,
            onValueChange = { viewModel.password = it },
            label = { Text("Password") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
//                visualTransformation = PasswordVisualTransformation(),
        )

        Spacer(Modifier.weight(1f))
        Button(
            onClick = onConnect,
            enabled = viewModel.address.isNotBlank()
        ) {
            Text("Connect")
        }
    }
    LaunchedEffect(cameraInfo) {
        if (cameraInfo != null) {
            viewModel.address = cameraInfo.host
        }
    }
}

@Composable
fun CameraDetailsContent(
    viewModel: MainViewModel,
    onCapture: () -> Unit,
    onStream: () -> Unit,
) {

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        SelectionContainer {
            Column {
                val explanationText = viewModel.explanationText.collectAsState().value
                Text(explanationText ?: "")
                Text("StreamUri: ${viewModel.streamUri}")
                Text("SnapshotUri: ${viewModel.snapshotUri}")
            }
        }

        Spacer(Modifier.weight(1f))

        Row {
            Button(
                onClick = onCapture,
                enabled = viewModel.snapshotUri != null
            ) {
                Text("Capture")
            }
            Spacer(Modifier.width(16.dp))
            Button(
                onClick = onStream,
                enabled = viewModel.streamUri != null
            ) {
                Text("Stream")
            }
        }
    }
}

@Composable
fun SnapshotContent(viewModel: MainViewModel) {
    val image by viewModel.image.collectAsState()

    Box(Modifier.fillMaxSize()) {
        if (image != null) {
            Image(
                modifier = Modifier.align(Alignment.Center),
                bitmap = image!!.toImageBitmap(),
                contentDescription = "snapshot"
            )
        } else {
            Text(
                modifier = Modifier.align(Alignment.Center),
                text = "Loading image",
            )
        }
    }
}

@Composable
fun StreamContent(viewModel: MainViewModel) {
    val streamUri = viewModel.streamUri

    Box(Modifier.fillMaxSize()) {
        if (streamUri != null) {
            val streamUrl = URLBuilder(streamUri).apply {
                user = viewModel.username
                password = viewModel.password
            }
                .buildString()
            Column(Modifier.fillMaxSize()) {
                Text("Stream URL: $streamUrl")
                StreamPlayer(url = streamUrl, modifier = Modifier.fillMaxWidth().weight(1f))
            }
        } else {
            Text(
                modifier = Modifier.align(Alignment.Center),
                text = "Error getting stream",
            )
        }
    }
}

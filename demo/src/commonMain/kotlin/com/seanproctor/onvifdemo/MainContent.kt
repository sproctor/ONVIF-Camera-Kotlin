package com.seanproctor.onvifdemo

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun MainContent(viewModel: MainViewModel) {
    val snapshot = viewModel.image.collectAsState().value
    val scaffoldState = rememberScaffoldState()
    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            if (snapshot == null) {
                TopAppBar(title = { Text("ONVIF Camera Demo") })
            } else {
                TopAppBar(
                    title = { Text("Snapshot") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSnapshot() }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                )
            }
        }
    ) { padding ->
        if (snapshot == null) {
            Column(Modifier.padding(padding)) {
                val discoveredDevices by viewModel.discoveredDevices.collectAsState()
                LazyColumn {
                    items(discoveredDevices.toList()) {
                        Box(Modifier.fillMaxWidth().clickable { viewModel.address.value = it.second }) {
                            Text(it.first)
                        }
                    }
                }
                Button(onClick = { viewModel.startDiscovery() }) {
                    Text("Scan")
                }

                val address by viewModel.address
                TextField(
                    value = address,
                    onValueChange = { viewModel.address.value = it },
                    label = { Text("Address") },
                    singleLine = true,
                )

                val username by viewModel.login
                TextField(
                    value = username,
                    onValueChange = { viewModel.login.value = it },
                    label = { Text("Username") },
                    singleLine = true,
                )

                val password by viewModel.password
                TextField(
                    value = password,
                    onValueChange = { viewModel.password.value = it },
                    label = { Text("Password") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
//                visualTransformation = PasswordVisualTransformation(),
                )

                Button(
                    onClick = viewModel::connectClicked,
                    enabled = address.isNotBlank()
                ) {
                    Text("Connect")
                }

                val explanationText = viewModel.explanationText.collectAsState().value
                if (explanationText != null) {
                    Text(explanationText)
                    val snapshotUri = viewModel.snapshotUri.value
                    Button(
                        onClick = { viewModel.getSnapshot() },
                        enabled = snapshotUri != null
                    ) {
                        Text("Capture")
                    }
                }
            }
        } else {
            Image(snapshot.toImageBitmap(), null)
        }
    }

    val errorText = viewModel.errorText.collectAsState().value
    LaunchedEffect(errorText) {
        if (errorText != null) {
            scaffoldState.snackbarHostState.showSnackbar(message = errorText, actionLabel = "Dismiss", duration = SnackbarDuration.Long)
            viewModel.clearErrorText()
        }
    }
}
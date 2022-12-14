package com.seanproctor.onvifdemo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Button
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

@Composable
fun MainContent(viewModel: MainViewModel) {
    Scaffold { padding ->
        Column(Modifier.padding(padding)) {
            // TODO display devices
            LazyColumn {

            }
            Button(onClick = { viewModel.startDiscovery() }) {
                Text("Scan")
            }

            val ipAddress by viewModel.ipAddress.collectAsState()
            TextField(ipAddress, onValueChange = { viewModel.ipAddress.value = it })

            val username by viewModel.login.collectAsState()
            TextField(username, onValueChange = { viewModel.login.value = it })

            val password by viewModel.password.collectAsState()
            TextField(password, onValueChange = { viewModel.password.value = it })

            Button(onClick = viewModel::connectClicked) {
                Text("Connect")
            }
        }
    }
}
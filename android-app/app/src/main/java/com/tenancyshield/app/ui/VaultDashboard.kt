package com.tenancyshield.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tenancyshield.app.data.Inspection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultDashboard(inspections: List<Inspection>, onLaunchCamera: () -> Unit, onViewPdf: (String) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("TenancyShield Vault") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onLaunchCamera) {
                Icon(Icons.Filled.Add, contentDescription = "New Inspection")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            items(inspections) { inspection ->
                Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Inspection ID: ${inspection.id}")
                        Text("Status: ${inspection.status}")
                        if (inspection.status == "Locked" && inspection.finalPdfPath != null) {
                            Button(onClick = { onViewPdf(inspection.finalPdfPath) }) {
                                Text("View BSA 2023 PDF")
                            }
                        }
                    }
                }
            }
        }
    }
}

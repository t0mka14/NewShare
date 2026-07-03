package org.example.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.example.app.navigation.RecorderComponent

@Composable
fun RecorderContent(component: RecorderComponent) {
    val state by component.state.subscribeAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recorder") },
                navigationIcon = {
                    IconButton(onClick = { component.onBack() }) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (state.isRecording) "RECORDING" else "IDLE",
                style = MaterialTheme.typography.h5,
                color = if (state.isRecording) Color.Red else Color.Gray
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(text = state.statusMessage, style = MaterialTheme.typography.body1)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { component.onRecord() },
                    enabled = !state.isRecording
                ) {
                    Text("Record")
                }
                
                Button(
                    onClick = { component.onStop() },
                    enabled = state.isRecording
                ) {
                    Text("Stop")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { component.onStartAgain() },
                    enabled = !state.isRecording && state.hasRecording
                ) {
                    Text("Start Again")
                }
                
                Button(
                    onClick = { component.onSave() },
                    enabled = !state.isRecording && state.hasRecording
                ) {
                    Text("Save")
                }
            }
            
            if (state.lastRecordingPath != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Last recording: ${state.lastRecordingPath}",
                    style = MaterialTheme.typography.caption
                )
            }
        }
    }
}

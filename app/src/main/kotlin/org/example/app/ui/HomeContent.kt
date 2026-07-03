package org.example.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.app.navigation.HomeComponent

@Composable
fun HomeContent(component: HomeComponent) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Clinical Recording App", style = MaterialTheme.typography.h4)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { component.onRecorderClicked() }) {
                Text("Go to Recorder")
            }
        }
    }
}

package io.ch0p.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable

/** Top-level navigation between the import flow and the model manager. */
@Composable
fun HomeScreen(initialVideo: Uri? = null) {
    var screen by rememberSaveable { mutableStateOf("import") }
    when (screen) {
        "models" -> ModelsScreen(onBack = { screen = "import" })
        else -> ImportScreen(initialVideo = initialVideo, onOpenModels = { screen = "models" })
    }
}

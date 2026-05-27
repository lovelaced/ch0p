package io.ch0p

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.ch0p.ui.HomeScreen
import io.ch0p.ui.theme.StudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val incoming = incomingVideo(intent)
        setContent {
            StudioTheme {
                HomeScreen(initialVideo = incoming)
            }
        }
    }

    /** A video shared into the app (ACTION_SEND) or opened directly (ACTION_VIEW). */
    private fun incomingVideo(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        Intent.ACTION_VIEW -> intent.data
        else -> null
    }
}

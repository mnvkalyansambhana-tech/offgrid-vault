package io.github.mnvkalyansambhana.offgridvault

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.mnvkalyansambhana.offgridvault.ui.AppNavHost
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SecureWindow.apply(this)
        // Every screen starts with a dark hero band (light status icons) over a light body.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            OffGridTheme {
                AppNavHost(container)
            }
        }
    }
}

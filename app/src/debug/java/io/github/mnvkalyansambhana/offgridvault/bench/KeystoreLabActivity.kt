package io.github.mnvkalyansambhana.offgridvault.bench

import android.app.KeyguardManager
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mnvkalyansambhana.offgridvault.SecureWindow
import io.github.mnvkalyansambhana.offgridvault.core.crypto.DeviceKey
import io.github.mnvkalyansambhana.offgridvault.ui.components.Body
import io.github.mnvkalyansambhana.offgridvault.ui.components.HeroBand
import io.github.mnvkalyansambhana.offgridvault.ui.components.Label
import io.github.mnvkalyansambhana.offgridvault.ui.components.PopButton
import io.github.mnvkalyansambhana.offgridvault.ui.components.SharpCard
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridColors
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridTheme
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug-only S21 experiment: does a Keystore key created with `setUnlockedDeviceRequired(true)`
 * survive changing and then removing the phone's screen lock? Uses a separate test alias —
 * never the real K_device.
 */
class KeystoreLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SecureWindow.apply(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val keyguard = getSystemService(KeyguardManager::class.java)
        setContent { OffGridTheme { Lab { keyguard.isDeviceSecure } } }
    }
}

private val testKey = DeviceKey(alias = "offgridvault.s21.labkey", unlockedDeviceRequired = true)
private val testData = "s21-probe".toByteArray()

@Composable
private fun Lab(deviceSecure: () -> Boolean) {
    val log = remember { mutableStateListOf<String>() }
    fun note(line: String) {
        log.add(0, SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date()) + "  " + line)
    }
    Column(Modifier.fillMaxSize()) {
        HeroBand(
            headline = "keystore lab.",
            label = "Debug only · S21",
            labelColor = OffGridColors.Mint,
            supporting = "1 create · 2 test · change screen lock · test · remove screen lock · test",
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PopButton("1 · Create test key", modifier = Modifier.fillMaxWidth(), onClick = {
                runCatching {
                    testKey.delete()
                    testKey.ensureExists()
                    "created · strongbox=${testKey.isStrongBoxBacked()} · screenLock=${deviceSecure()}"
                }.fold({ note(it) }, { note("CREATE FAILED: ${it.javaClass.simpleName}") })
            })
            PopButton("2 · Test key", modifier = Modifier.fillMaxWidth(), onClick = {
                note(
                    runCatching {
                        if (!testKey.exists()) return@runCatching "KEY GONE (deleted by the system)"
                        val ok = testKey.open(testKey.seal(testData, testData), testData).contentEquals(testData)
                        if (ok) "works · screenLock=${deviceSecure()}" else "MISMATCH"
                    }.getOrElse { "FAILED: ${it.javaClass.simpleName} · screenLock=${deviceSecure()}" },
                )
            })
            PopButton("Delete test key", modifier = Modifier.fillMaxWidth(), onClick = {
                testKey.delete()
                note("deleted")
            })
            SharpCard(Modifier.fillMaxWidth()) {
                Label("Log (newest first)", style = OffGridType.LabelSmall)
                if (log.isEmpty()) Body("nothing yet", color = OffGridColors.TextOnLight2)
                log.forEach { Body(it, style = OffGridType.Secret) }
            }
        }
    }
}

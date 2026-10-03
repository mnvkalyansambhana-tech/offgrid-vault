package io.github.mnvkalyansambhana.offgridvault.bench

import android.app.ActivityManager
import android.graphics.Color
import android.os.Build
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mnvkalyansambhana.offgridvault.SecureWindow
import io.github.mnvkalyansambhana.offgridvault.core.crypto.AndroidSodium
import io.github.mnvkalyansambhana.offgridvault.core.crypto.Argon2Calibrator
import io.github.mnvkalyansambhana.offgridvault.core.crypto.Argon2Params
import io.github.mnvkalyansambhana.offgridvault.core.crypto.Argon2id
import io.github.mnvkalyansambhana.offgridvault.core.crypto.Randomness
import io.github.mnvkalyansambhana.offgridvault.core.crypto.wipe
import io.github.mnvkalyansambhana.offgridvault.ui.components.Body
import io.github.mnvkalyansambhana.offgridvault.ui.components.HeroBand
import io.github.mnvkalyansambhana.offgridvault.ui.components.Label
import io.github.mnvkalyansambhana.offgridvault.ui.components.PopButton
import io.github.mnvkalyansambhana.offgridvault.ui.components.SharpCard
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridColors
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridDimens
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridTheme
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Debug-only M1 tool: measures Argon2id (64 MiB, p = 1) on this phone and shows what the C14
 * calibration would choose. Uses throwaway random input; touches no vault data.
 */
class Argon2BenchmarkActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SecureWindow.apply(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val memoryClassMb = getSystemService(ActivityManager::class.java).memoryClass
        setContent { OffGridTheme { BenchmarkScreen(memoryClassMb) } }
    }
}

@Composable
private fun BenchmarkScreen(memoryClassMb: Int) {
    val scope = rememberCoroutineScope()
    val lines = remember { mutableStateListOf<String>() }
    var running by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        HeroBand(
            headline = "argon2 bench.",
            label = "Debug only · M1",
            labelColor = OffGridColors.Mint,
            supporting = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} · " +
                "${Build.SUPPORTED_ABIS.first()} · heap class $memoryClassMb MB",
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(OffGridDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SharpCard(Modifier.fillMaxWidth()) {
                Label("Results", style = OffGridType.LabelSmall)
                if (lines.isEmpty()) Body("tap run. takes ~10–20 s.", color = OffGridColors.TextOnLight2)
                lines.forEach { Body(it, style = OffGridType.Secret) }
            }
            PopButton(
                text = if (running) "Running…" else "Run benchmark",
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    running = true
                    lines.clear()
                    scope.launch {
                        val results = withContext(Dispatchers.Default) { runBenchmark { line -> lines += line } }
                        lines += results
                        running = false
                    }
                },
            )
        }
    }
}

private suspend fun runBenchmark(progress: (String) -> Unit): List<String> {
    val argon2 = AndroidSodium.argon2id
    val out = mutableListOf<String>()
    withContext(Dispatchers.Main) { progress("warming up…") }
    timeMillis(argon2, Argon2Params(Argon2Params.MIN_ITERATIONS))
    for (t in 2..6) {
        val ms = timeMillis(argon2, Argon2Params(t))
        withContext(Dispatchers.Main) { progress("t=$t  m=64MiB  p=1  →  $ms ms") }
    }
    val calibration = Argon2Calibrator(argon2).calibrate()
    out += "calibrated: t=${calibration.params.iterations} (floor run ${calibration.floorMillis} ms)"
    out += "target ${Argon2Calibrator.TARGET_MILLIS} ms, floor t=${Argon2Params.MIN_ITERATIONS}"
    return out
}

private fun timeMillis(argon2: Argon2id, params: Argon2Params): Long {
    val password = Randomness.bytes(16)
    val start = System.nanoTime()
    argon2.deriveKey(password, Argon2id.newSalt(), params).wipe()
    password.wipe()
    return (System.nanoTime() - start) / 1_000_000
}

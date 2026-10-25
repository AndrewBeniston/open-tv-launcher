package app.opentvlauncher.ui.home

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.opentvlauncher.ui.theme.Label
import app.opentvlauncher.ui.theme.LauncherColors
import app.opentvlauncher.ui.theme.LocalLauncherType
import kotlinx.coroutines.delay
import java.util.Date

/** The clock, top right. It follows the TV's 12 or 24 hour setting and changes on the minute. */
@Composable
fun Clock(modifier: Modifier = Modifier, color: Color = LauncherColors.onSurfaceVariant) {
    val context = LocalContext.current
    val time by produceState(formatNow(context), context) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            value = formatNow(context)
        }
    }
    Label(time, LocalLauncherType.current.clock, color, modifier)
}

private fun formatNow(context: Context): String = DateFormat.getTimeFormat(context).format(Date())

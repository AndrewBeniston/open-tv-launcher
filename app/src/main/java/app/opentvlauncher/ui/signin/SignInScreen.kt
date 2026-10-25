package app.opentvlauncher.ui.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentvlauncher.ui.focus.PillButton
import app.opentvlauncher.ui.home.TintWash
import app.opentvlauncher.ui.theme.Label
import app.opentvlauncher.ui.theme.LauncherColors
import app.opentvlauncher.ui.theme.LocalLauncherType
import app.opentvlauncher.ui.theme.SquircleShape
import app.opentvlauncher.ui.theme.StarLoader
import kotlinx.coroutines.delay

private val Margin = 58.dp
private val SignInWash = Color(0x29963C32)

/**
 * The first start on each TV (design, "Trakt sign-in"). The TV shows Trakt's device code,
 * The user approves it on a phone, and the launcher then renews its own sign-in.
 * "Get a New Code" has focus because it is safe to press.
 */
@Composable
fun SignInScreen(state: SignInState, onNewCode: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    val type = LocalLauncherType.current
    val newCode = remember { FocusRequester() }
    LaunchedEffect(Unit) { newCode.requestFocus() }
    val code = state as? SignInState.Code

    Box(modifier.fillMaxSize()) {
        TintWash(tint = { SignInWash }, centerX = 0.85f, centerY = 0.10f, modifier = Modifier.fillMaxSize())
        Label("Personal TV Launcher", type.eyebrow, LauncherColors.onSurfaceVariant, Modifier.at(75.dp))
        Label("Sign In to Trakt", type.bannerTitle, LauncherColors.onSurface, Modifier.at(94.dp))
        Label(
            "Use your phone to sign this TV in.",
            type.synopsis,
            LauncherColors.onSurfaceVariant,
            Modifier.at(148.dp).width(530.dp),
            maxLines = 2,
        )
        val url = code?.verificationUrl?.removePrefix("https://")?.removePrefix("http://") ?: "trakt.tv/activate"
        Step(1, Modifier.at(210.dp)) {
            append("On your phone, open ")
            withStyle(SpanStyle(color = LauncherColors.onSurface, fontWeight = FontWeight.Medium)) { append(url) }
        }
        Step(2, Modifier.at(254.dp)) { append("Enter this code") }
        Label(
            code?.let { formatUserCode(it.userCode) } ?: "No Code",
            type.signInCode,
            if (code != null) LauncherColors.onSurface else LauncherColors.onSurfaceFaint,
            Modifier
                .at(300.dp)
                .background(LauncherColors.surfaceContainerHigh, SquircleShape(16.dp))
                .padding(horizontal = 24.dp, vertical = 12.dp),
        )
        Row(Modifier.at(398.dp), verticalAlignment = Alignment.CenterVertically) {
            StarLoader(spinning = code != null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Label(statusLine(state), type.meta, LauncherColors.onSurfaceVariant)
        }
        Row(Modifier.at(444.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Get a New Code", onNewCode, Modifier.focusRequester(newCode))
            PillButton("Skip for Now", onSkip)
        }
    }
}

private fun Modifier.at(top: Dp) = offset(x = Margin, y = top)

@Composable
private fun statusLine(state: SignInState): String = when (state) {
    SignInState.Loading -> "Getting a Trakt Code"
    is SignInState.Code -> {
        val now by produceState(System.currentTimeMillis(), state) {
            while (true) {
                value = System.currentTimeMillis()
                delay(1_000L - value % 1_000L)
            }
        }
        val left = ((state.expiresAt - now) / 1000).coerceAtLeast(0)
        "Code Expires in ${left / 60}:${(left % 60).toString().padStart(2, '0')}"
    }
    SignInState.Expired -> "The code expired. Select Get a New Code."
    SignInState.Denied -> "Approval was denied. Select Get a New Code."
    SignInState.Offline -> "Could not get a Trakt code. Check the network and select Get a New Code."
    SignInState.Failed -> "Could not read the Trakt response. Select Get a New Code."
    SignInState.NotConfigured -> "Trakt sign-in is not configured. Install a configured build or select Skip for Now."
    SignInState.Approved -> "Approved"
}

@Composable
private fun Step(number: Int, modifier: Modifier, text: androidx.compose.ui.text.AnnotatedString.Builder.() -> Unit) {
    val type = LocalLauncherType.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(28.dp)
                .background(LauncherColors.surfaceContainerHigh, SquircleShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Label(number.toString(), type.label.copy(fontSize = 14.sp, lineHeight = 18.sp), LauncherColors.onSurface)
        }
        Spacer(Modifier.width(12.dp))
        BasicText(
            buildAnnotatedString(text),
            style = type.synopsis.copy(fontSize = 18.sp, lineHeight = 22.sp, color = LauncherColors.onSurfaceVariant),
        )
    }
}

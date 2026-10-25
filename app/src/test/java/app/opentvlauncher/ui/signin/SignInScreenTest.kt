package app.opentvlauncher.ui.signin

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.opentvlauncher.ui.theme.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The sign-in screen from the design: Trakt's URL and code, and focus on "Get a New Code". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SignInScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var newCodes = 0
    private var skips = 0

    private fun show(state: SignInState) {
        compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
        compose.setContent {
            LauncherTheme { SignInScreen(state, onNewCode = { newCodes++ }, onSkip = { skips++ }) }
        }
        compose.waitForIdle()
    }

    @Test
    fun showsTheCodeAndTheUrlFromTrakt() {
        show(SignInState.Code("8F3A21C7", "https://trakt.tv/activate", System.currentTimeMillis() + 600_000))
        compose.onNodeWithText("8F3A 21C7").assertExists()
        compose.onNodeWithText("On your phone, open trakt.tv/activate").assertExists()
        compose.onNodeWithText("Code Expires in", substring = true).assertExists()
    }

    @Test
    fun getANewCodeHasFocusAndSkipWorks() {
        show(SignInState.Expired)
        compose.onNode(isFocused()).assert(hasText("Get a New Code"))
        compose.onNodeWithText("Skip for Now").performClick()
        compose.onNodeWithText("Get a New Code").performClick()
        assertEquals(1, skips)
        assertEquals(1, newCodes)
        compose.onNodeWithText("The code expired. Select Get a New Code.").assertExists()
    }
}

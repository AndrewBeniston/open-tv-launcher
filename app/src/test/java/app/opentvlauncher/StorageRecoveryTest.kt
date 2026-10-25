package app.opentvlauncher

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.opentvlauncher.settings.TitleAction
import app.opentvlauncher.trakt.SignIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** A damaged preferences file must not stop the launcher from starting. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi", application = OfflineLauncherApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StorageRecoveryTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: LauncherApp get() = ApplicationProvider.getApplicationContext()

    /** A protobuf field that says 127 bytes follow, then only four. */
    private fun damage(name: String) {
        val file = File(app.filesDir, "datastore/$name.preferences_pb")
        file.parentFile!!.mkdirs()
        file.writeBytes(byteArrayOf(0x0a, 0x7f, 0x13, 0x37, -1, -1))
    }

    @Test
    fun damagedFilesGiveSignedOutAndTheDefaultSettings() {
        damage("trakt")
        damage("settings")
        assertEquals(SignIn.SignedOut, runBlocking { app.session.state.first() })
        assertEquals(TitleAction.ShowDetails, runBlocking { app.settings.titleAction.first() })
        // The damaged files are replaced, so "Skip for Now" and a settings change are saved.
        runBlocking { app.session.skip() }
        assertEquals(SignIn.Skipped, runBlocking { app.session.state.first() })
        runBlocking { app.settings.setTitleAction(TitleAction.OpenInStremio) }
        assertEquals(TitleAction.OpenInStremio, runBlocking { app.settings.titleAction.first() })
    }

    @Test
    fun aDamagedTokenFileStillShowsTheSignInScreen() {
        damage("trakt")
        damage("settings")
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Sign In to Trakt").fetchSemanticsNodes().isNotEmpty() }
        }
    }
}

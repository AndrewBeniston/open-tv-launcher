package app.opentvlauncher.ui.home

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import app.opentvlauncher.data.CardAction
import app.opentvlauncher.data.PosterRow
import app.opentvlauncher.data.SampleHome
import app.opentvlauncher.data.WideRow
import app.opentvlauncher.home.Title
import app.opentvlauncher.home.TitleKind
import app.opentvlauncher.ui.holdTvKey
import app.opentvlauncher.ui.releaseTvKey
import app.opentvlauncher.ui.sendTvKey
import app.opentvlauncher.ui.theme.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 24 Oct 2026: the menu key, or OK held, on a Continue Watching card opens its menu. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeLongPressTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var focusManager: FocusManager
    private val opened = mutableListOf<String>()
    private val menus = mutableListOf<String>()

    private fun open(name: String) = CardAction.OpenTitle(Title(TitleKind.Movie, name))

    /** The sample Home with a title behind every Continue Watching card and poster. */
    private val content = SampleHome.content.let { home ->
        home.copy(
            rows = home.rows.map { row ->
                when (row) {
                    is WideRow -> row.copy(cards = row.cards.map { it.copy(action = open(it.title)) })
                    is PosterRow -> row.copy(posters = row.posters.map { it.copy(action = open(it.title)) })
                }
            },
        )
    }

    private fun CardAction.name() = (this as CardAction.OpenTitle).title.title

    @Before
    fun setUp() {
        compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            focusManager = LocalFocusManager.current
            LauncherTheme {
                HomeScreen(
                    content,
                    homeRequests = 0,
                    onOpenApp = {},
                    onOpenCard = { opened += it.name() },
                    onCardMenu = { menus += it.name() },
                )
            }
        }
        settle()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun press(keyCode: Int) {
        compose.sendTvKey(keyCode, focusManager)
        settle()
    }

    @Test
    fun holdingOkOnAContinueWatchingCardOpensItsMenuOnly() {
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.holdTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
        settle()
        compose.releaseTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
        settle()
        assertEquals(listOf("Tales of the Quiet Coast"), menus)
        assertEquals(emptyList<String>(), opened)
    }

    @Test
    fun theMenuKeyOpensTheMenu() {
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        press(KeyEvent.KEYCODE_MENU)
        assertEquals(listOf("Harbour Lights"), menus)
        assertEquals(emptyList<String>(), opened)
    }

    @Test
    fun aShortOkOpensTheCard() {
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf("Tales of the Quiet Coast"), opened)
        assertEquals(emptyList<String>(), menus)
        // A long press ends; the next short OK clicks as usual.
        compose.holdTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.releaseTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
        settle()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf("Tales of the Quiet Coast", "Tales of the Quiet Coast"), opened)
    }

    /** Other rows have no menu: OK held on a poster opens it when released. */
    @Test
    fun holdingOkOnAPosterOpensItOnRelease() {
        repeat(3) { press(KeyEvent.KEYCODE_DPAD_DOWN) }
        compose.holdTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.releaseTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
        settle()
        assertEquals(listOf("Ember"), opened)
        assertEquals(emptyList<String>(), menus)
    }
}

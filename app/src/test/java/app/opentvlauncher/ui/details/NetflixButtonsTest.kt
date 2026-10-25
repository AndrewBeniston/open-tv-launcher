package app.opentvlauncher.ui.details

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.opentvlauncher.data.Art
import app.opentvlauncher.data.BannerSlide
import app.opentvlauncher.data.CardAction
import app.opentvlauncher.data.HomeContent
import app.opentvlauncher.data.SlideKind
import app.opentvlauncher.home.HomeMapper
import app.opentvlauncher.home.Title
import app.opentvlauncher.home.TitleChange
import app.opentvlauncher.home.TitleKind
import app.opentvlauncher.system.TitleApp
import app.opentvlauncher.system.TitleAppIcons
import app.opentvlauncher.system.installTitleApps
import app.opentvlauncher.system.readTitleApps
import app.opentvlauncher.ui.downTvKey
import app.opentvlauncher.ui.home.HomeScreen
import app.opentvlauncher.ui.menu.TitleMenu
import app.opentvlauncher.ui.menu.TitleMenuStatus
import app.opentvlauncher.ui.releaseTvKey
import app.opentvlauncher.ui.sendTvKey
import app.opentvlauncher.ui.theme.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The app buttons (25 Oct 2026): each app's icon and name, Stremio first, then Netflix
 * for a title on Netflix. An app that is not installed has no button.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NetflixButtonsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val dontLookUp = Title(TitleKind.Movie, "Don't Go Back", year = 2021, imdb = "tt91000004", traktId = 1, netflixId = "81252357")
    private val paused = dontLookUp.copy(progress = 0.4f)

    @Before
    fun setUp() {
        // A TV has no touchscreen, so its window is never in touch mode. Robolectric starts in it.
        compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
    }

    private fun details(title: Title, apps: TitleAppIcons, onPlay: () -> Unit = {}, onNetflix: () -> Unit = {}) = compose.setContent {
        LauncherTheme(titleApps = apps) {
            DetailsScreen(title, WatchlistButton.Add, onPlay = onPlay, onNetflix = onNetflix, onWatchlist = {}, onBack = {})
        }
    }

    private fun left(text: String) = compose.onNodeWithText(text).getUnclippedBoundsInRoot().left

    private fun named(name: String) = SemanticsMatcher("named $name") { hasText(name).matches(it) || hasContentDescription(name).matches(it) }

    @Test
    fun detailsShowStremioThenNetflixThenTheWatchlist() {
        installTitleApps()
        var played = 0
        var netflix = 0
        details(dontLookUp, readTitleApps(), onPlay = { played++ }, onNetflix = { netflix++ })
        compose.waitForIdle()
        compose.onNode(isFocused()).assert(hasText("Stremio"))
        assertTrue(left("Stremio") < left("Netflix"))
        assertTrue(left("Netflix") < left("Add to Watchlist"))
        compose.onNodeWithText("Netflix").performClick()
        assertEquals(0 to 1, played to netflix)
        compose.onNodeWithText("Stremio").performClick()
        assertEquals(1 to 1, played to netflix)
    }

    @Test
    fun noButtonSaysPlayIn() {
        installTitleApps()
        details(dontLookUp, readTitleApps())
        compose.onNodeWithText("Play in", substring = true).assertDoesNotExist()
    }

    @Test
    fun aTitleInProgressSaysResumeThenNetflix() {
        installTitleApps()
        details(paused, readTitleApps())
        compose.waitForIdle()
        compose.onNode(isFocused()).assert(hasText("Resume"))
        assertTrue(left("Resume") < left("Netflix"))
    }

    @Test
    fun withoutNetflixInstalledThereIsNoNetflixButton() {
        installTitleApps(TitleApp.Stremio)
        details(dontLookUp, readTitleApps())
        compose.onNodeWithText("Stremio").assertExists()
        compose.onNodeWithText("Netflix").assertDoesNotExist()
    }

    @Test
    fun aTitleThatIsNotOnNetflixHasNoNetflixButton() {
        installTitleApps()
        details(dontLookUp.copy(netflixId = null), readTitleApps())
        compose.onNodeWithText("Stremio").assertExists()
        compose.onNodeWithText("Netflix").assertDoesNotExist()
    }

    @Test
    fun withoutStremioNetflixComesFirstAndHasFocus() {
        installTitleApps(TitleApp.Netflix)
        details(dontLookUp, readTitleApps())
        compose.waitForIdle()
        compose.onNodeWithText("Stremio").assertDoesNotExist()
        compose.onNode(isFocused()).assert(hasText("Netflix"))
    }

    @Test
    fun withNoAppButtonTheWatchlistButtonHasFocus() {
        details(dontLookUp, readTitleApps())
        compose.waitForIdle()
        compose.onNode(isFocused()).assert(hasText("Add to Watchlist"))
    }

    @Test
    fun withNoButtonAtAllThePageStillOpens() {
        details(Title(TitleKind.Movie, "Not on Trakt"), readTitleApps())
        compose.waitForIdle()
        compose.onNodeWithText("Not on Trakt").assertExists()
    }

    @Test
    fun beforeTheAppsAreReadStremioShowsAndNetflixWaits() {
        details(dontLookUp, TitleAppIcons.Unknown)
        compose.onNodeWithText("Stremio").assertExists()
        compose.onNodeWithText("Netflix").assertDoesNotExist()
    }

    @Test
    fun theHandOffNamesNetflix() {
        compose.setContent { LauncherTheme { HandOffScreen(dontLookUp, onBack = {}, app = TitleApp.Netflix) } }
        compose.onNodeWithText("Opening in Netflix").assertExists()
        compose.onNodeWithText("Waiting for Netflix").assertExists()
        compose.onNodeWithText("Stremio", substring = true).assertDoesNotExist()
    }

    @Test
    fun theHandOffStillNamesStremioForStremio() {
        compose.setContent { LauncherTheme { HandOffScreen(dontLookUp, onBack = {}) } }
        compose.onNodeWithText("Opening in Stremio").assertExists()
        compose.onNodeWithText("Waiting for Stremio").assertExists()
    }

    private fun menu(title: Title, apps: TitleAppIcons, onNetflix: () -> Unit = {}, onBack: () -> Unit = {}) = compose.setContent {
        LauncherTheme(titleApps = apps) {
            TitleMenu(
                title = title,
                changes = listOf(TitleChange.MarkWatched),
                status = TitleMenuStatus.Ready,
                onPlay = {},
                onNetflix = onNetflix,
                onChoose = {},
                onConfirmEnded = {},
                onBack = onBack,
            )
        }
    }

    private fun top(description: String) = compose.onNodeWithContentDescription(description).getUnclippedBoundsInRoot().top

    @Test
    fun theMenuOffersNetflixUnderResume() {
        installTitleApps()
        var netflix = 0
        menu(paused, readTitleApps(), onNetflix = { netflix++ })
        compose.waitForIdle()
        compose.onNode(isFocused()).assert(named("Resume"))
        compose.onNodeWithText("Opens the title in Netflix.").assertExists()
        assertTrue(top("Resume") < top("Netflix"))
        assertTrue(top("Netflix") < top("Mark as Watched"))
        compose.onNodeWithContentDescription("Netflix").performClick()
        assertEquals(1, netflix)
    }

    /** Review M3: as on the banner and the details page, an app that is not installed has no row. */
    @Test
    fun withoutStremioTheMenuHasNoResumeAndNetflixHasFocus() {
        installTitleApps(TitleApp.Netflix)
        menu(paused, readTitleApps())
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Resume").assertDoesNotExist()
        compose.onNodeWithText("Opens the title in Stremio.").assertDoesNotExist()
        compose.onNode(isFocused()).assert(named("Netflix"))
    }

    /** 25 Oct 2026: with neither app, the first row is Close, and OK on it closes the menu. */
    @Test
    fun withNoAppTheMenuStartsOnClose() {
        var closed = 0
        menu(paused, readTitleApps(), onBack = { closed++ })
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Resume").assertDoesNotExist()
        compose.onNodeWithContentDescription("Netflix").assertDoesNotExist()
        compose.onNode(isFocused()).assert(named("Close"))
        compose.onNodeWithText("Closes the menu.").assertExists()
        assertTrue(top("Close") < top("Mark as Watched"))
        compose.downTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.releaseTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitForIdle()
        assertEquals(1, closed)
    }

    @Test
    fun withAnAppTheMenuHasNoClose() {
        installTitleApps(TitleApp.Netflix)
        menu(paused, readTitleApps())
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Close").assertDoesNotExist()
    }

    /**
     * Review R7-1: the apps are read during an OK on Resume, and Stremio is not there. Resume goes;
     * the press on it ends with no choice, and the first row, Close, takes focus with no press.
     */
    @Test
    fun aPressOnARowThatGoesChoosesNothing() {
        var icons by mutableStateOf(TitleAppIcons.Unknown)
        val noApps = readTitleApps()
        var played = 0
        var closed = 0
        val chosen = mutableListOf<TitleChange>()
        compose.setContent {
            LauncherTheme(titleApps = icons) {
                TitleMenu(
                    title = paused,
                    changes = listOf(TitleChange.MarkWatched, TitleChange.MarkUnwatched),
                    status = TitleMenuStatus.Ready,
                    onPlay = { played++ },
                    onChoose = { chosen += it },
                    onConfirmEnded = {},
                    onBack = { closed++ },
                )
            }
        }
        compose.waitForIdle()
        compose.onNode(isFocused()).assert(named("Resume"))
        compose.downTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.runOnUiThread {
            icons = noApps
            Snapshot.sendApplyNotifications()
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Resume").assertDoesNotExist()
        compose.onNode(isFocused()).assert(named("Close"))
        compose.releaseTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitForIdle()
        assertEquals(Triple(0, 0, emptyList<TitleChange>()), Triple(played, closed, chosen))
        // A new press on the row that has focus chooses it.
        compose.downTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.releaseTvKey(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitForIdle()
        assertEquals(Triple(0, 1, emptyList<TitleChange>()), Triple(played, closed, chosen))
    }

    @Test
    fun withNoRowAtAllTheMenuStillOpens() {
        compose.setContent {
            LauncherTheme(titleApps = readTitleApps()) {
                TitleMenu(Title(TitleKind.Show, "Fern", traktId = 1), emptyList(), TitleMenuStatus.Ready, {}, {}, {}, {})
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Fern").assertExists()
    }

    @Test
    fun theMenuHasNoNetflixRowForATitleNotOnNetflix() {
        installTitleApps()
        menu(paused.copy(netflixId = null), readTitleApps())
        compose.onNodeWithContentDescription("Netflix").assertDoesNotExist()
    }

    @Test
    fun theMenuHasNoNetflixRowWithoutNetflixInstalled() {
        installTitleApps(TitleApp.Stremio)
        menu(paused, readTitleApps())
        compose.onNodeWithContentDescription("Netflix").assertDoesNotExist()
    }

    private lateinit var focusManager: FocusManager

    /** A page over Home: false while it covers Home, and one more return each time it closes. */
    private var homeActive by mutableStateOf(true)
    private var homeReturns by mutableIntStateOf(0)
    private var bannerTitle by mutableStateOf(dontLookUp)

    private fun bannerWith(title: Title, apps: TitleAppIcons, onPlay: (CardAction) -> Unit) {
        bannerTitle = title
        compose.mainClock.autoAdvance = false
        compose.setContent {
            focusManager = LocalFocusManager.current
            val slide = BannerSlide(
                key = "pick",
                kind = SlideKind.TopPick,
                eyebrow = "Top Pick for You",
                title = bannerTitle.title,
                meta = HomeMapper.kindLine(bannerTitle),
                synopsis = "",
                art = Art(),
                action = CardAction.OpenTitle(bannerTitle),
            )
            LauncherTheme(titleApps = apps) {
                HomeScreen(
                    HomeContent(slides = listOf(slide), apps = HomeMapper.apps, rows = emptyList()),
                    homeRequests = 0,
                    onOpenApp = {},
                    onPlay = onPlay,
                    active = homeActive,
                    focusReturns = homeReturns,
                )
            }
        }
        settle()
    }

    /** The hand-off, or another page, covers Home and then closes; [meanwhile] runs while it covers. */
    private fun coverAndReturn(meanwhile: () -> Unit = {}) {
        compose.runOnUiThread {
            homeActive = false
            Snapshot.sendApplyNotifications()
        }
        settle()
        compose.runOnUiThread {
            meanwhile()
            homeActive = true
            homeReturns++
            Snapshot.sendApplyNotifications()
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
    fun theBannerShowsStremioNetflixThenDetails() {
        installTitleApps()
        val played = mutableListOf<CardAction>()
        bannerWith(dontLookUp, readTitleApps()) { played += it }
        press(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNode(isFocused()).assert(named("Stremio"))
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNode(isFocused()).assert(named("Netflix"))
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNode(isFocused()).assert(named("Details"))
        assertEquals(listOf<CardAction>(CardAction.OpenNetflix(dontLookUp)), played)
    }

    /** Review M2: Back from the Netflix hand-off returns to Netflix, not to Stremio. */
    @Test
    fun aReturnToHomeFocusesTheBannerButtonThatWasPressed() {
        installTitleApps()
        bannerWith(dontLookUp, readTitleApps()) {}
        press(KeyEvent.KEYCODE_DPAD_UP)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNode(isFocused()).assert(named("Netflix"))
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        coverAndReturn()
        compose.onNode(isFocused()).assert(named("Netflix"))
        // And Details after the details page.
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        coverAndReturn()
        compose.onNode(isFocused()).assert(named("Details"))
    }

    @Test
    fun aBannerButtonThatHasGoneGivesFocusToTheFirst() {
        installTitleApps()
        bannerWith(dontLookUp, readTitleApps()) {}
        press(KeyEvent.KEYCODE_DPAD_UP)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNode(isFocused()).assert(named("Netflix"))
        // While Netflix was in front the title left Netflix.
        coverAndReturn { bannerTitle = dontLookUp.copy(netflixId = null) }
        // Only the apps row's Netflix tile is left.
        compose.onAllNodesWithText("Netflix").assertCountEquals(1)
        compose.onNode(isFocused()).assert(named("Stremio"))
    }

    /**
     * Review R8-1: the focused button goes while the details page shows, as Netflix does when its
     * answer stops counting. The first button left takes focus. A button that goes without focus
     * leaves focus where it is.
     */
    @Test
    fun theDetailsPageHandsFocusOnWhenTheFocusedButtonGoes() {
        installTitleApps()
        var title by mutableStateOf(dontLookUp)
        var played = 0
        lateinit var focus: FocusManager
        compose.setContent {
            focus = LocalFocusManager.current
            LauncherTheme(titleApps = readTitleApps()) {
                Box {
                    // Something focusable earlier on the screen: Android's own choice when focus is lost.
                    Box(Modifier.size(10.dp).focusable())
                    DetailsScreen(title, WatchlistButton.Add, onPlay = { played++ }, onWatchlist = {}, onBack = {})
                }
            }
        }
        compose.waitForIdle()
        compose.sendTvKey(KeyEvent.KEYCODE_DPAD_RIGHT, focus)
        compose.waitForIdle()
        compose.onNode(isFocused()).assert(hasText("Netflix"))
        compose.runOnUiThread {
            title = dontLookUp.copy(netflixId = null)
            Snapshot.sendApplyNotifications()
        }
        compose.waitForIdle()
        compose.onNode(isFocused()).assert(hasText("Stremio"))
        compose.sendTvKey(KeyEvent.KEYCODE_DPAD_CENTER, focus)
        compose.waitForIdle()
        assertEquals(1, played)
        // On the watchlist button, Netflix coming and going changes nothing.
        compose.runOnUiThread {
            title = dontLookUp
            Snapshot.sendApplyNotifications()
        }
        compose.waitForIdle()
        compose.sendTvKey(KeyEvent.KEYCODE_DPAD_RIGHT, focus)
        compose.sendTvKey(KeyEvent.KEYCODE_DPAD_RIGHT, focus)
        compose.waitForIdle()
        compose.onNode(isFocused()).assert(hasText("Add to Watchlist"))
        compose.runOnUiThread {
            title = dontLookUp.copy(netflixId = null)
            Snapshot.sendApplyNotifications()
        }
        compose.waitForIdle()
        compose.onNode(isFocused()).assert(hasText("Add to Watchlist"))
    }

    /** The details page too: after the Netflix hand-off, Netflix has focus again. */
    @Test
    fun backFromNetflixOnTheDetailsPageReturnsToNetflix() {
        installTitleApps()
        var active by mutableStateOf(true)
        lateinit var focus: FocusManager
        compose.setContent {
            focus = LocalFocusManager.current
            LauncherTheme(titleApps = readTitleApps()) {
                DetailsScreen(dontLookUp, WatchlistButton.Add, onPlay = {}, onNetflix = { active = false }, onWatchlist = {}, onBack = {}, active = active)
            }
        }
        compose.waitForIdle()
        compose.sendTvKey(KeyEvent.KEYCODE_DPAD_RIGHT, focus)
        compose.waitForIdle()
        compose.onNode(isFocused()).assert(hasText("Netflix"))
        compose.sendTvKey(KeyEvent.KEYCODE_DPAD_CENTER, focus)
        compose.waitForIdle()
        compose.runOnUiThread { active = true }
        compose.waitForIdle()
        compose.onNode(isFocused()).assert(hasText("Netflix"))
    }

    @Test
    fun theBannerHidesNetflixForATitleNotOnNetflix() {
        installTitleApps()
        bannerWith(dontLookUp.copy(netflixId = null), readTitleApps()) {}
        press(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNode(isFocused()).assert(named("Stremio"))
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNode(isFocused()).assert(named("Details"))
    }

    @Test
    fun withoutStremioTheBannerStartsOnNetflix() {
        installTitleApps(TitleApp.Netflix)
        bannerWith(dontLookUp, readTitleApps()) {}
        press(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNode(isFocused()).assert(named("Netflix"))
    }

    @Test
    fun withNoAppTheBannerStartsOnDetails() {
        bannerWith(dontLookUp, readTitleApps()) {}
        press(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNode(isFocused()).assert(named("Details"))
    }

    @Test
    fun theAppIconsComeFromThePackageManager() {
        installTitleApps()
        val apps = readTitleApps()
        assertEquals(setOf(TitleApp.Stremio.packageName, TitleApp.Netflix.packageName), apps.installed)
        assertTrue(apps.icons.values.all { it != null })
    }
}

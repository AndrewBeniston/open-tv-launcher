package app.opentvlauncher.ui.home

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import app.opentvlauncher.data.PosterRow
import app.opentvlauncher.data.SampleHome
import app.opentvlauncher.data.WideRow
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
import kotlin.math.hypot
import kotlin.math.max

/**
 * Text clearance on Home (24 Oct 2026: a focused poster almost touched its row header).
 * For every row and for the first, middle and last item, with focus on it and every spring at
 * rest, it checks:
 *
 * 1. The focused item, at its focus scale, keeps [HomeLayout.textClearance] (12 dp) from every
 *    visible text that is not inside it: row headers, card titles, banner text, the clock.
 * 2. Its visible shadow does not touch a text above its bottom edge. Below the item the shadow
 *    falls behind the titles, as on Google TV.
 * 3. It keeps [HomeLayout.textClearance] from the items of the rows above and below, and it does
 *    not overlap the next item in its own row.
 * 4. No two visible texts overlap. A text is measured by its lines, not its padded box.
 * 5. Headers, app names and banner lines are not cut short. Card titles and the synopsis may
 *    end in an ellipsis: the design gives them a line limit.
 *
 * The shadow is the design's: y 12 dp, blur 28 dp, black 60 %. As a CSS shadow its blur is a
 * Gaussian with sigma 14 dp. It is at least 10 % dark up to 13.5 dp past its edge, so it shows
 * 1.5 dp above the item, 13.5 dp at each side and 25.5 dp below.
 */
abstract class HomeClearanceSuite {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var focusManager: FocusManager

    @Before
    fun setUp() {
        compose.runOnUiThread { compose.activity.window.decorView.requestFocusFromTouch() }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            focusManager = LocalFocusManager.current
            LauncherTheme { HomeScreen(SampleHome.content, homeRequests = 0, onOpenApp = {}) }
        }
        settle()
    }

    /** Long enough for the focus, scroll and reveal springs to come to rest. */
    private fun settle() {
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
    }

    private fun press(keyCode: Int) {
        compose.sendTvKey(keyCode, focusManager)
        settle()
    }

    private fun right(times: Int = 1) = repeat(times) { press(KeyEvent.KEYCODE_DPAD_RIGHT) }

    private data class Box(val name: String, val rect: Rect, val node: SemanticsNode)

    /**
     * The node's box on screen in dp, with its scale and without clipping. boundsInRoot clips to
     * each parent, so a focused poster that grows past the top of its row would look shorter.
     */
    private fun SemanticsNode.dpRect(): Rect {
        val d = compose.density.density
        val coordinates = layoutInfo.coordinates
        val r = coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
        return Rect(r.left / d, r.top / d, r.right / d, r.bottom / d)
    }

    private fun SemanticsNode.name(): String =
        (config.getOrNull(SemanticsProperties.Text)?.joinToString() ?: config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString())
            ?: config.getOrNull(SemanticsProperties.TestTag) ?: "node $id"

    private fun screen(): Rect = compose.onRoot().fetchSemanticsNode().dpRect()

    private fun visible(rect: Rect) = rect.overlaps(screen()) && rect.width > 0f && rect.height > 0f

    private fun SemanticsNode.isInside(other: SemanticsNode): Boolean {
        var parent = this.parent
        while (parent != null) {
            if (parent.id == other.id) return true
            parent = parent.parent
        }
        return false
    }

    /**
     * The texts on screen, from the merged tree: headers, banner lines, the clock, and the titles
     * of the focused row. Text inside a tile, card or button belongs to that item. Titles of
     * rows without focus are hidden, and their semantics are cleared, so they are left out.
     */
    private fun texts(): List<Box> {
        val text = compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Text) and SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick),
        ).fetchSemanticsNodes()
        val titles = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, CARD_TITLES_TAG)).fetchSemanticsNodes()
        return (text + titles).map { Box(it.name(), it.lineRect(), it) }.filter { visible(it.rect) }
    }

    /**
     * A text's lines, from the top of the first line to the bottom of the last, at the text's
     * scale. A text's own box can be taller than its lines (the banner title's box is 52 dp
     * for a 44 dp line), and the space above the capitals is not ink.
     */
    private fun SemanticsNode.lineRect(): Rect {
        val box = dpRect()
        val layout = textLayout() ?: return box
        if (layout.lineCount == 0) return box
        val scale = box.height / (layout.size.height / compose.density.density)
        val d = compose.density.density
        return Rect(
            box.left,
            box.top + layout.getLineTop(0) / d * scale,
            box.right,
            box.top + layout.getLineBottom(layout.lineCount - 1) / d * scale,
        )
    }

    private fun SemanticsNode.textLayout(): TextLayoutResult? {
        val results = mutableListOf<TextLayoutResult>()
        config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.firstOrNull()
    }

    private fun items(): List<Box> = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
        .fetchSemanticsNodes().map { Box(it.name(), it.dpRect(), it) }.filter { visible(it.rect) }

    /** Distance between two boxes; negative when they overlap. */
    private fun gap(a: Rect, b: Rect): Float {
        val dx = max(0f, max(b.left - a.right, a.left - b.right))
        val dy = max(0f, max(b.top - a.bottom, a.top - b.bottom))
        return if (dx == 0f && dy == 0f) -1f else hypot(dx, dy)
    }

    private fun Rect.withShadow() = Rect(left - SHADOW_SIDE, top - SHADOW_TOP, right + SHADOW_SIDE, bottom + SHADOW_BOTTOM)

    /** Checks the screen as it is now and returns what breaks the rules. */
    private fun violations(where: String): List<String> {
        val problems = mutableListOf<String>()
        val focusedNode = compose.onNode(isFocused()).fetchSemanticsNode()
        val focused = Box(focusedNode.name(), focusedNode.dpRect(), focusedNode)
        val clearance = HomeLayout.textClearance.value
        val texts = texts()
        val others = texts.filterNot { it.node.isInside(focused.node) || it.node.id == focused.node.id }

        others.forEach { text ->
            val g = gap(focused.rect, text.rect)
            if (g < clearance) problems += "$where: '${focused.name}' is ${"%.1f".format(g)} dp from text '${text.name}' (needs $clearance)"
            if (text.rect.top < focused.rect.bottom && gap(focused.rect.withShadow(), text.rect) < 0f) {
                problems += "$where: the shadow of '${focused.name}' touches text '${text.name}'"
            }
        }

        val items = items().filterNot { it.node.id == focused.node.id || it.node.isInside(focused.node) || focused.node.isInside(it.node) }
        items.forEach { item ->
            val sameRow = item.rect.top < focused.rect.bottom && item.rect.bottom > focused.rect.top
            val g = gap(focused.rect, item.rect)
            if (sameRow && g < 0f) problems += "$where: '${focused.name}' overlaps '${item.name}' in its row"
            if (!sameRow && g < clearance) problems += "$where: '${focused.name}' is ${"%.1f".format(g)} dp from '${item.name}' in the next row"
        }

        texts.forEachIndexed { i, a ->
            texts.drop(i + 1).forEach { b ->
                val nested = a.node.isInside(b.node) || b.node.isInside(a.node)
                if (!nested && gap(a.rect, b.rect) < 0f) problems += "$where: text '${a.name}' overlaps text '${b.name}'"
            }
        }

        val synopses = SampleHome.content.slides.map { it.synopsis }.toSet()
        texts.filter { it.node.config.contains(SemanticsProperties.Text) && it.name !in synopses }.forEach { text ->
            if (text.node.isCutShort()) problems += "$where: text '${text.name}' is cut short"
        }
        return problems
    }

    private fun SemanticsNode.isCutShort(): Boolean {
        val layout = textLayout() ?: return false
        return (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
    }

    private fun assertClear(problems: List<String>) = assertTrue(problems.joinToString("\n", prefix = "\n"), problems.isEmpty())

    /** Down from the apps row to content row [row], then along it: first, middle and last item. */
    private fun checkRow(row: Int): List<String> {
        repeat(row + 1) { press(KeyEvent.KEYCODE_DPAD_DOWN) }
        val content = SampleHome.content.rows[row]
        val count = when (content) {
            is WideRow -> content.cards.size
            is PosterRow -> content.posters.size
        }
        val problems = violations("${content.title}, first item").toMutableList()
        right(count / 2)
        problems += violations("${content.title}, middle item")
        right(count)
        problems += violations("${content.title}, last item")
        return problems
    }

    @Test
    fun theFocusScaleIsMeasuredInTheBounds() {
        repeat(3) { press(KeyEvent.KEYCODE_DPAD_DOWN) }
        val poster = compose.onNode(isFocused()).fetchSemanticsNode().dpRect()
        assertEquals(HomeLayout.poster.height.value * HomeLayout.FOCUS_SCALE, poster.height, 0.5f)
    }

    @Test
    fun theAppsRowAtRestAndAlongItIsClear() {
        val problems = violations("Apps row, YouTube").toMutableList()
        right()
        problems += violations("Apps row, Stremio")
        right()
        problems += violations("Apps row, Netflix")
        right()
        problems += violations("Apps row, BBC iPlayer")
        right()
        problems += violations("Apps row, All Apps")
        assertClear(problems)
    }

    @Test
    fun theOpenBannerIsClear() {
        press(KeyEvent.KEYCODE_DPAD_UP)
        val problems = violations("Banner, first button").toMutableList()
        right()
        problems += violations("Banner, Details")
        assertClear(problems)
    }

    @Test
    fun theContinueWatchingRowIsClear() = assertClear(checkRow(0))

    @Test
    fun theTopPicksRowIsClear() = assertClear(checkRow(1))

    @Test
    fun theWatchlistRowIsClear() = assertClear(checkRow(2))

    @Test
    fun theYouTubeRowIsClear() = assertClear(checkRow(3))

    private companion object {
        const val SHADOW_TOP = 1.5f
        const val SHADOW_SIDE = 13.5f
        const val SHADOW_BOTTOM = 25.5f
    }
}

/** The Chromecast: 1080p at 2x, 960 x 540 dp. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeClearanceChromecastTest : HomeClearanceSuite()

/** A 1080p TV at 1.5x, 1280 x 720 dp: the Philips' size is not confirmed, so the other common TV size is checked too. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], qualifiers = "w1280dp-h720dp-land-television-hdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeClearanceLargeTvTest : HomeClearanceSuite()

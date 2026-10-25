package app.opentvlauncher.ui.focus

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * Remembers which item of a row had focus last and sends focus back to it when focus enters
 * the row again (design: "Each row remembers its last focused item").
 *
 * Items are remembered by key, so a refresh that reorders a row keeps the same title. When the
 * remembered item has gone, focus goes to the item now at its place, or the last item.
 *
 * Compose's own focusRestorer matches items by their composition hash. The items of a lazy row
 * share one hash, so it always went back to the first item.
 *
 * With [firstWhenNewAhead] (Continue Watching, 25 Oct 2026), focus that enters with the
 * D-pad ([enter]) goes to the first item when new items, not in the row when focus last left it,
 * are ahead of the remembered one. An item that only moved ahead is not new. A return from a page
 * such as Details uses [focusRemembered], so it always comes back to the item that was opened.
 */
class RowFocusMemory {
    private val requesters = mutableMapOf<Any, FocusRequester>()
    internal var keys: List<Any> = emptyList()
        set(value) {
            field = value
            requesters.keys.retainAll(value.toSet())
        }
    internal var lastKey: Any? = null
    internal var lastIndex = 0

    var firstWhenNewAhead = false

    /** The row's items when focus last left it. Null until it has. */
    private var keysWhenLeft: Set<Any>? = null

    /**
     * Set by a lazy row: scrolls the item at the index into the row, then runs the action. A lazy
     * row keeps its first item on screen when items arrive ahead of it, so the item to focus may
     * not be composed.
     */
    var scrollTo: ((index: Int, then: () -> Unit) -> Unit)? = null

    internal fun requester(key: Any): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    /** True while the remembered item is still in the row. */
    val hasRemembered: Boolean get() = lastKey == null || lastKey in keys

    /** The remembered item, or the item now at its place when it has gone. Null for an empty row. */
    private fun target(): FocusRequester? {
        if (keys.isEmpty()) return null
        val key = lastKey?.takeIf { it in keys } ?: keys[lastIndex.coerceIn(0, keys.lastIndex)]
        return requester(key)
    }

    /** Moves focus to the item at [index] and makes it the one to remember. */
    fun focus(index: Int = 0) {
        val key = keys.getOrNull(index) ?: return
        lastKey = key
        lastIndex = index
        requester(key).requestFocus()
    }

    /**
     * Moves focus to the remembered item, or the item now at its place. False if none takes it.
     * The group's onEnter runs for this request too, so the new items noted by [left] are
     * forgotten first: they must not send it to the first item.
     */
    fun focusRemembered(): Boolean {
        keysWhenLeft = null
        return target()?.requestFocus() == true
    }

    /** Focus has left the row: notes its items. */
    fun left() {
        if (rememberedIndex() != null) keysWhenLeft = keys.toSet()
    }

    /** True when a new item, not in the row when focus last left it, is ahead of the remembered one. */
    private val newAhead: Boolean
        get() {
            val before = keysWhenLeft ?: return false
            val index = rememberedIndex() ?: return false
            return keys.subList(0, index).any { it !in before }
        }

    /**
     * Focus enters the row with the D-pad: the first item after new arrivals ahead
     * ([firstWhenNewAhead]), otherwise the remembered item or the item now at its place. An item
     * that is not composed is scrolled into the row first ([scrollTo]). False for an empty row,
     * or when the item cannot take focus and there is no way to scroll to it.
     */
    fun enter(): Boolean {
        if (keys.isEmpty()) return false
        val index = if (firstWhenNewAhead && newAhead) 0 else rememberedIndex() ?: lastIndex.coerceIn(0, keys.lastIndex)
        val key = keys[index]
        lastKey = key
        lastIndex = index
        if (requester(key).requestFocus()) return true
        val scroll = scrollTo ?: return false
        scroll(index) { requester(key).requestFocus() }
        return true
    }

    private fun rememberedIndex(): Int? = lastKey?.let(keys::indexOf)?.takeIf { it >= 0 }

    companion object {
        /**
         * Keeps the remembered item while the row is off screen, such as under All Apps, or
         * when Android recreates the launcher with its saved state. Keys are strings or indices.
         */
        val Saver: Saver<RowFocusMemory, Any> = listSaver(
            save = { listOf(it.lastKey, it.lastIndex) },
            restore = { saved -> RowFocusMemory().apply { lastKey = saved[0]; lastIndex = saved[1] as Int } },
        )
    }
}

/** A memory for a row whose items are fixed: each item's key is its index. */
@Composable
fun rememberRowFocusMemory(count: Int): RowFocusMemory = rememberRowFocusMemory(remember(count) { List(count) { it } })

/** A memory for a row of items with stable [keys]. */
@Composable
fun rememberRowFocusMemory(keys: List<Any>): RowFocusMemory = remember { RowFocusMemory() }.also { it.keys = keys }

/** Makes this row one focus group that returns focus to the item [memory] remembers. */
fun Modifier.rememberedFocusGroup(memory: RowFocusMemory): Modifier = this
    .focusProperties {
        onEnter = {
            // A redirect inside onEnter replaces the focus move that was in progress.
            // An item that is not composed refuses; then the move goes ahead as it was.
            memory.enter()
        }
    }
    .onFocusChanged { if (!it.hasFocus) memory.left() }
    .focusGroup()

/** Marks the focusable item at [index] of a fixed row that uses [rememberedFocusGroup]. */
fun Modifier.rememberedFocusItem(memory: RowFocusMemory, index: Int): Modifier = rememberedFocusItem(memory, index, index)

/** Marks the focusable item with [key], now at [index], of a row that uses [rememberedFocusGroup]. */
fun Modifier.rememberedFocusItem(memory: RowFocusMemory, key: Any, index: Int): Modifier = this
    .focusRequester(memory.requester(key))
    .onFocusChanged {
        if (it.isFocused) {
            memory.lastKey = key
            memory.lastIndex = index
        }
    }

/** As [rememberRowFocusMemory], kept as saved state, for a page that leaves composition, such as the Apps tab. */
@Composable
fun rememberSavedRowFocusMemory(count: Int): RowFocusMemory {
    val keys = remember(count) { List(count) { it } }
    return rememberSaveable(saver = RowFocusMemory.Saver) { RowFocusMemory() }.also { it.keys = keys }
}

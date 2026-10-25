package app.opentvlauncher.ui.focus

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalWindowInfo

/** The keys clickable takes as OK. */
internal val KeyEvent.isSelect: Boolean
    get() = key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter || key == Key.Spacebar

/**
 * OK on an item: [onClick] on the release of a press that began on the item, and [onLongPress],
 * when there is one, for OK held until it repeats or for the remote's menu key. Put it before
 * clickable, which then never sees OK and keeps only touch and accessibility clicks.
 *
 * A press ends without a click when Android cancels its release, when focus leaves the item, or
 * when the window gains or loses focus during it: the release may then go to another window. The
 * repeats and release of a press that began elsewhere do nothing, so the long press that opens
 * the Continue Watching menu cannot also choose a row in it, or open the next card's menu.
 * [interaction] gets the press, its release or its cancel.
 */
internal fun Modifier.okPress(
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
): Modifier = this then OkPressElement(interaction, onClick, onLongPress)

private data class OkPressElement(
    val interaction: MutableInteractionSource,
    val onClick: () -> Unit,
    val onLongPress: (() -> Unit)?,
) : ModifierNodeElement<OkPressNode>() {
    override fun create() = OkPressNode(interaction, onClick, onLongPress)

    override fun update(node: OkPressNode) {
        if (node.interaction != interaction) node.cancel()
        node.interaction = interaction
        node.onClick = onClick
        node.onLongPress = onLongPress
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "okPress"
    }
}

private class OkPressNode(
    var interaction: MutableInteractionSource,
    var onClick: () -> Unit,
    var onLongPress: (() -> Unit)?,
) : Modifier.Node(), KeyInputModifierNode, FocusEventModifierNode, ObserverModifierNode, CompositionLocalConsumerModifierNode {
    /** The press that began on this item, until its release, its long press or its cancel. */
    private var press: PressInteraction.Press? = null

    override fun onPreKeyEvent(event: KeyEvent): Boolean {
        val down = event.type == KeyEventType.KeyDown
        val repeat = event.nativeKeyEvent.repeatCount
        val longPress = onLongPress
        if (event.key == Key.Menu && longPress != null) {
            if (down && repeat == 0) longPress()
            return down
        }
        if (!event.isSelect) return false
        when {
            down && repeat == 0 -> {
                cancel()
                val started = PressInteraction.Press(Offset.Zero)
                press = started
                interaction.tryEmit(started)
                // Only while the press lasts, so idle items watch nothing.
                observeReads { currentValueOf(LocalWindowInfo).isWindowFocused }
            }
            down -> if (press != null && longPress != null) {
                cancel()
                longPress()
            }
            event.type == KeyEventType.KeyUp -> {
                val ended = press ?: return true
                if (event.nativeKeyEvent.isCanceled) {
                    cancel()
                } else {
                    end()
                    interaction.tryEmit(PressInteraction.Release(ended))
                    onClick()
                }
            }
        }
        return true
    }

    override fun onKeyEvent(event: KeyEvent) = false

    override fun onFocusEvent(focusState: FocusState) {
        if (!focusState.isFocused) cancel()
    }

    /** The window gained or lost focus during the press. */
    override fun onObservedReadsChanged() = cancel()

    override fun onDetach() = cancel()

    /** Ends the press with no click. */
    fun cancel() {
        val ended = press ?: return
        end()
        interaction.tryEmit(PressInteraction.Cancel(ended))
    }

    private fun end() {
        press = null
        if (isAttached) observeReads { }
    }
}

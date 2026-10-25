package app.opentvlauncher.ui.focus

import androidx.compose.foundation.focusGroup
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.clearAndSetSemantics

/**
 * A full-screen page that another page can cover, such as Home under the details page. While
 * it is not [active], focus cannot enter it, from the D-pad or from code, and TalkBack sees none
 * of its actions. Hiding it with alpha alone left its cards focusable under the page on top.
 * While [accepts] is false, the page also refuses focus that comes from outside it.
 */
fun Modifier.activePage(active: Boolean, accepts: () -> Boolean = { true }): Modifier = this
    .focusProperties { onEnter = { if (!active || !accepts()) cancelFocusChange() } }
    .focusGroup()
    // Last in the chain, so that a change of [active] adds or removes only this element.
    .then(if (active) Modifier else Modifier.clearAndSetSemantics {})

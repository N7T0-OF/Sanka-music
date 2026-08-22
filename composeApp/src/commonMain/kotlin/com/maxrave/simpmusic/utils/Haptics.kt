package com.maxrave.simpmusic.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * In-memory mirror of the vibration settings, kept up to date by [com.maxrave.simpmusic.viewModel.SharedViewModel]
 * collecting the DataStore flows. Any composable can trigger haptics without threading the
 * settings through the whole tree.
 */
object HapticsState {
    var enabled: Boolean = true
    var intensity: Int = 50
}

/**
 * Fires a haptic tick honoring the Vibration settings (ON/OFF + intensity).
 * The Compose haptic API only exposes a few feedback types, so intensity is mapped to
 * the closest available type: subtle below 35%, strong from there on.
 */
@Composable
fun performHaptic() {
    performHapticWith(LocalHapticFeedback.current)
}

/** Non-composable variant for use inside local functions of a composable. */
fun performHapticWith(feedback: HapticFeedback) {
    if (!HapticsState.enabled) return
    val level = HapticsState.intensity
    if (level <= 0) return
    feedback.performHapticFeedback(
        if (level < 35) {
            HapticFeedbackType.TextHandleMove
        } else {
            HapticFeedbackType.LongPress
        },
    )
}

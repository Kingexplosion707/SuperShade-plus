package com.supershade.plus

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the media pop-up is pinned: kept up (through song changes) until let go or swiped away.
 * Shared so the shade's live activity can pin it too, and show whether it is.
 */
object PopupPin {
    private val _pinned = MutableStateFlow(false)
    val pinned: StateFlow<Boolean> = _pinned
    var value: Boolean
        get() = _pinned.value
        set(v) { _pinned.value = v }
}

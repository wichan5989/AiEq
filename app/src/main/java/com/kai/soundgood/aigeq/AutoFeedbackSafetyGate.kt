package com.kai.soundgood.aigeq

/**
 * Fail-closed gate for any future OSC EQ write path.
 * This class does not send OSC and must be checked at the actual write boundary.
 */
class AutoFeedbackSafetyGate {
    var autoEnabled: Boolean = false
        private set
    var operatorArmed: Boolean = false
        private set
    var liveRouteVerified: Boolean = false
        private set
    var mixerStateVerified: Boolean = false
        private set
    var targetLocked: Boolean = false
        private set
    var lastWriteMs: Long = Long.MIN_VALUE
        private set

    fun enableAuto(): Boolean {
        // UI may request auto mode, but it remains disarmed until explicitly armed.
        autoEnabled = true
        operatorArmed = false
        return true
    }

    fun armAfterOperatorConfirmation() { operatorArmed = autoEnabled }
    fun updateReadiness(route: Boolean, mixer: Boolean, target: Boolean) {
        liveRouteVerified = route
        mixerStateVerified = mixer
        targetLocked = target
        if (!route || !mixer || !target) operatorArmed = false
    }
    fun disable() { autoEnabled = false; operatorArmed = false }
    fun onDisconnect() { disable(); liveRouteVerified = false; mixerStateVerified = false }
    fun canApply(gainDb: Float, nowMs: Long, confidence: Float, persistentMs: Long): Boolean {
        if (!autoEnabled || !operatorArmed || !liveRouteVerified || !mixerStateVerified || !targetLocked) return false
        if (!gainDb.isFinite() || gainDb >= 0f || gainDb < -3f) return false
        if (!confidence.isFinite() || confidence < 0.95f || persistentMs < 1000L) return false
        if (lastWriteMs != Long.MIN_VALUE && nowMs - lastWriteMs < 5000L) return false
        return true
    }
    fun recordSuccessfulWrite(nowMs: Long) { lastWriteMs = nowMs }
}

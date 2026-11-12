package com.kai.soundgood.aigeq

/** Fail-closed UI mode: this policy never authorizes a mixer write. */
enum class AiOperatingMode { MONITOR_ONLY, ASSIST_CONFIRM }

data class ProposedPoint(val target: String, val hz: Float, val cutDb: Float, val token: Long)

class AiModeController {
    var mode: AiOperatingMode = AiOperatingMode.MONITOR_ONLY
        private set
    var pending: ProposedPoint? = null
        private set
    var previewConfirmed: Boolean = false
        private set

    fun switchMode(next: AiOperatingMode) {
        mode = next
        clearPending()
    }
    fun clearPending() { pending = null; previewConfirmed = false }
    fun propose(point: ProposedPoint): Boolean {
        clearPending()
        if (mode != AiOperatingMode.ASSIST_CONFIRM || point.hz <= 0f ||
            point.cutDb >= 0f || point.cutDb < -3f || !point.hz.isFinite() || !point.cutDb.isFinite()) return false
        pending = point
        return true
    }
    fun confirmPreview(token: Long): Boolean {
        val point = pending ?: return false
        if (mode != AiOperatingMode.ASSIST_CONFIRM || point.token != token) return false
        previewConfirmed = true
        return true
    }
    // Always false until verified mixer-specific OSC writer, readback and undo are implemented.
    fun mayWriteToMixer(): Boolean = false
}

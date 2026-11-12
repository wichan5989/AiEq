package com.kai.soundgood.aigeq

/** Independent monotonic-time guard for UI/AI decisions; does not control the mixer. */
class LiveAudioSessionGuard(private val freshForMs: Long = 1200) {
    init { require(freshForMs > 0) }
    private var lastCompleteFrameAt: Long? = null
    private var active = false
    private var routeOk = false
    private var completedFrames = 0L

    fun begin() { active = true; routeOk = false; lastCompleteFrameAt = null; completedFrames = 0 }
    fun routeVerified(ok: Boolean) { routeOk = ok; if (!ok) lastCompleteFrameAt = null }
    fun completeFrame(nowElapsedMs: Long) {
        if (active && routeOk) { lastCompleteFrameAt = nowElapsedMs; completedFrames++ }
    }
    fun end() { active = false; routeOk = false; lastCompleteFrameAt = null }
    fun status(nowElapsedMs: Long): LiveAudioSessionStatus {
        val age = lastCompleteFrameAt?.let { (nowElapsedMs - it).coerceAtLeast(0) }
        val fresh = active && routeOk && age != null && age <= freshForMs
        return LiveAudioSessionStatus(active, routeOk, completedFrames, age, fresh)
    }
}

data class LiveAudioSessionStatus(
    val capturing: Boolean,
    val routeVerified: Boolean,
    val completeFrames: Long,
    val lastFrameAgeMs: Long?,
    val canUseForAdvice: Boolean
)

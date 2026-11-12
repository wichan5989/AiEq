package com.kai.soundgood.aigeq

/** Conservative readiness assessment. Never infers mixer audio from a UI-only selection. */
enum class AudioFeed { ANDROID_MIC, USB_PCM, VERIFIED_MIXER_STREAM }
data class ReadinessInput(
    val oscConnected: Boolean,
    val audioRunning: Boolean,
    val feed: AudioFeed,
    val mixerSourceSelected: Boolean,
    val routingVerified: Boolean,
    val eqSnapshotComplete: Boolean
)
data class ReadinessResult(val canAnalyze: Boolean, val canWrite: Boolean, val warnings: List<String>)
object LiveReadiness {
    fun evaluate(input: ReadinessInput): ReadinessResult {
        val warnings = mutableListOf<String>()
        if (!input.oscConnected) warnings += "M32 OSC not connected"
        if (!input.audioRunning) warnings += "No active PCM capture"
        if (input.mixerSourceSelected && input.feed != AudioFeed.VERIFIED_MIXER_STREAM)
            warnings += "Selected CH/Bus is UI-only; analyzer does not receive that mixer source"
        if (!input.routingVerified) warnings += "EQ / FX routing not verified"
        if (!input.eqSnapshotComplete) warnings += "EQ baseline snapshot missing or incomplete"
        warnings += "Live EQ/Gate writes disabled in v2.6"
        return ReadinessResult(input.audioRunning, false, warnings)
    }
}

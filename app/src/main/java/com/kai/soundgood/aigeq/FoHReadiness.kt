package com.kai.soundgood.aigeq

/** Fail-closed live readiness checklist. Never represents an actual hardware test. */
data class FoHReadiness(
    val oscRepliesVerified: Boolean = false,
    val spectrumInputVerified: Boolean = false,
    val fxRoutingVerified: Boolean = false,
    val normalizedGainMappingVerified: Boolean = false,
    val isolatedBusTestPassed: Boolean = false,
    val readbackAndUndoTestPassed: Boolean = false,
    val operatorPresent: Boolean = false
) {
    val readyForControlledHardwareTrial: Boolean get() =
        oscRepliesVerified && spectrumInputVerified && fxRoutingVerified &&
        normalizedGainMappingVerified && isolatedBusTestPassed &&
        readbackAndUndoTestPassed && operatorPresent
    val liveEqWritesEnabled: Boolean get() = false // deliberately disabled in this build
    fun missing(): List<String> = buildList {
        if (!oscRepliesVerified) add("OSC reply verification")
        if (!spectrumInputVerified) add("real source PCM/spectrum verification")
        if (!fxRoutingVerified) add("FX insert routing verification")
        if (!normalizedGainMappingVerified) add("EQ gain encoding verification")
        if (!isolatedBusTestPassed) add("disconnected bus hardware trial")
        if (!readbackAndUndoTestPassed) add("readback and undo hardware trial")
        if (!operatorPresent) add("FOH operator confirmation")
    }
}

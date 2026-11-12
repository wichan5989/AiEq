package com.kai.soundgood.aigeq

/** RTA routing is independent of EQ destination. Source selection does NOT provide PCM audio over OSC. */
enum class RtaSourceMode { FOLLOW, MANUAL, MEASUREMENT_MIC }
data class RtaSource(val target: EqTarget) { val label get() = target.label }

/** Lock freezes the resolved source at the time of locking; layer changes do not override it. */
class RtaSourceRouter(initial: RtaSource = RtaSource(EqTarget(TargetKind.BUS, 1))) {
    var mode: RtaSourceMode = RtaSourceMode.FOLLOW; private set
    var manual: RtaSource = initial; private set
    var measurementMicChannel: Int = 32; private set
    var locked: Boolean = false; private set
    private var frozen: RtaSource? = null
    fun setMode(value: RtaSourceMode, selected: EqTarget) { mode = value; if (locked) frozen = resolveUnlocked(selected) }
    fun setManual(value: RtaSource) { manual = value; if (locked) frozen = value }
    fun setMeasurementMic(channel: Int) { require(channel in 1..32); measurementMicChannel = channel; if (locked && mode == RtaSourceMode.MEASUREMENT_MIC) frozen = RtaSource(EqTarget(TargetKind.CHANNEL,channel)) }
    fun setLocked(value: Boolean, selected: EqTarget) { if(value && !locked) frozen = resolveUnlocked(selected); if(!value) frozen=null; locked=value }
    fun resolve(selected: EqTarget): RtaSource = frozen ?: resolveUnlocked(selected)
    private fun resolveUnlocked(selected: EqTarget): RtaSource = when(mode) {
        RtaSourceMode.FOLLOW -> RtaSource(selected)
        RtaSourceMode.MANUAL -> manual
        RtaSourceMode.MEASUREMENT_MIC -> RtaSource(EqTarget(TargetKind.CHANNEL,measurementMicChannel))
    }
}

package com.kai.soundgood.aigeq

/** Capability is UNKNOWN until an actual console state read verifies it. */
enum class TargetKind { CHANNEL, BUS, MAIN_LR, MATRIX }
enum class EqKind { PEQ, GEQ, OFF }
enum class Capability { UNKNOWN, AVAILABLE, UNAVAILABLE }
data class EqTarget(val kind: TargetKind, val number: Int = 1) {
    val label: String get() = when (kind) {
        TargetKind.CHANNEL -> "CH %02d".format(number)
        TargetKind.BUS -> "BUS %02d".format(number)
        TargetKind.MAIN_LR -> "MAIN LR"
        TargetKind.MATRIX -> "MTX %02d".format(number)
    }
    val peqBands: Int get() = if (kind == TargetKind.CHANNEL) 4 else 6
}
data class EqCapabilities(
    val peq: Capability = Capability.UNKNOWN,
    val geq: Capability = Capability.UNKNOWN,
    val fxSlot: Int? = null,
    val routingVerified: Boolean = false
)
/** GEQ is not intrinsic to a channel/bus. Only mark AVAILABLE after verifying FX routing. */
fun allowedEq(target: EqTarget, caps: EqCapabilities, kind: EqKind): Boolean = when (kind) {
    EqKind.OFF -> true
    EqKind.PEQ -> caps.peq == Capability.AVAILABLE
    EqKind.GEQ -> caps.geq == Capability.AVAILABLE && caps.routingVerified && caps.fxSlot != null
}

/** Follow layers means following the selected target in THIS app; external console sync is not implemented. */
class FollowLayerController(initial: EqTarget = EqTarget(TargetKind.BUS, 1)) {
    var target: EqTarget = initial
        private set
    var following: Boolean = true
        private set
    fun setFollowing(value: Boolean) { following = value }
    fun onMixerSelection(target: EqTarget) { if (following) this.target = target }
    fun selectDirectly(target: EqTarget) { this.target = target }
}

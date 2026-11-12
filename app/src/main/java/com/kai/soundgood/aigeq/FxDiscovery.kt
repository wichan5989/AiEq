package com.kai.soundgood.aigeq

/** Conservative capability discovery: no FX slot is assumed from a bus number. */
data class FxSlotObservation(val slot: Int, val algorithmName: String?, val insertedOn: EqTarget?, val verified: Boolean)
data class FxDiscoveryResult(val slots: List<FxSlotObservation>, val diagnostic: String) {
    fun verifiedGeqFor(target: EqTarget): FxSlotObservation? = slots.firstOrNull {
        it.verified && it.insertedOn == target && it.algorithmName?.contains("GEQ", ignoreCase = true) == true
    }
}
class FxDiscovery {
    /** Only externally validated slot/insert observations can be accepted. */
    fun fromObservations(observations: List<FxSlotObservation>): FxDiscoveryResult {
        val safe = observations.filter { it.slot in 1..8 }.distinctBy { it.slot }
        return FxDiscoveryResult(safe, "No FX mapping is inferred. Verify algorithm, slot and insert routing on the console.")
    }
    fun capabilities(target: EqTarget, peqSnapshot: M32PeqSnapshot?, result: FxDiscoveryResult): EqCapabilities =
        EqCapabilities(
            peq = if (peqSnapshot?.target == target && peqSnapshot.complete) Capability.AVAILABLE else Capability.UNKNOWN,
            geq = if (result.verifiedGeqFor(target) != null) Capability.AVAILABLE else Capability.UNKNOWN,
            fxSlot = result.verifiedGeqFor(target)?.slot,
            routingVerified = result.verifiedGeqFor(target) != null
        )
}

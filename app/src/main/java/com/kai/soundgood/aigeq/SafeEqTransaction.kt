package com.kai.soundgood.aigeq

/** Pure planning and simulated verification. No transport write method is exposed. */
data class EqBandKey(val target: EqTarget, val tool: EqKind, val band: Int)
data class EqPlan(val key: EqBandKey, val baselineDb: Double, val observedDb: Double,
                  val proposedDb: Double, val confirmed: Boolean, val sourceVerified: Boolean)
sealed class EqPlanResult {
    data class Accepted(val plan: EqPlan): EqPlanResult()
    data class Rejected(val reason: String): EqPlanResult()
}
class SafeEqPlanner(private val maximumCutDb: Double = 3.0) {
    init { require(maximumCutDb in 0.0..3.0) }
    fun validate(plan: EqPlan, capabilities: EqCapabilities): EqPlanResult {
        if (!plan.confirmed) return EqPlanResult.Rejected("FOH confirmation required")
        if (!plan.sourceVerified) return EqPlanResult.Rejected("RTA source not verified")
        if (!listOf(plan.baselineDb,plan.observedDb,plan.proposedDb).all { it.isFinite() }) return EqPlanResult.Rejected("Invalid gain")
        if (plan.key.band !in 1..(if (plan.key.tool == EqKind.PEQ) plan.key.target.peqBands else 31)) return EqPlanResult.Rejected("Invalid band")
        if (!allowedEq(plan.key.target, capabilities, plan.key.tool) || plan.key.tool == EqKind.OFF) return EqPlanResult.Rejected("EQ capability unknown")
        if (kotlin.math.abs(plan.baselineDb - plan.observedDb) > 0.05) return EqPlanResult.Rejected("Concurrent mixer change")
        if (plan.proposedDb > plan.baselineDb || plan.proposedDb < plan.baselineDb - maximumCutDb) return EqPlanResult.Rejected("Cumulative cut limit")
        return EqPlanResult.Accepted(plan)
    }
}
/** A simulator of readback/undo semantics; NEVER sends OSC. */
class SimulatedEqStore {
    private val gains = mutableMapOf<EqBandKey, Double>()
    fun seed(key: EqBandKey, gain: Double) { gains[key] = gain }
    fun read(key: EqBandKey): Double? = gains[key]
    fun apply(accepted: EqPlanResult.Accepted): Boolean {
        val p=accepted.plan
        if (gains[p.key] != p.observedDb) return false
        gains[p.key]=p.proposedDb
        return gains[p.key]==p.proposedDb
    }
    fun undo(accepted: EqPlanResult.Accepted): Boolean {
        val p=accepted.plan
        if (gains[p.key] != p.proposedDb) return false
        gains[p.key]=p.baselineDb
        return gains[p.key]==p.baselineDb
    }
}

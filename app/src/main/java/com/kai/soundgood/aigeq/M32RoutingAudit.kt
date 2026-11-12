package com.kai.soundgood.aigeq

/** Read-only FX/GEQ routing audit. No assumptions from FX slot numbers. */
data class RoutingEvidence(val target: EqTarget, val slot: Int, val algorithm: String?, val insertPath: String?, val algorithmConfirmed: Boolean, val insertConfirmed: Boolean)
data class RoutingAudit(val geqAvailable: Boolean, val reasons: List<String>)
class M32RoutingAuditor {
 fun audit(target: EqTarget, evidence: List<RoutingEvidence>): RoutingAudit {
  val matches=evidence.filter { it.target==target && it.slot in 1..8 }
  val valid=matches.any { it.algorithmConfirmed && it.insertConfirmed && it.algorithm?.contains("GEQ",true)==true && !it.insertPath.isNullOrBlank() }
  val reasons=buildList {
   if(matches.isEmpty()) add("No verified FX routing evidence for target")
   if(matches.none { it.algorithmConfirmed && it.algorithm?.contains("GEQ",true)==true }) add("GEQ algorithm not confirmed")
   if(matches.none { it.insertConfirmed && !it.insertPath.isNullOrBlank() }) add("Insert routing not confirmed")
  }
  return RoutingAudit(valid,reasons)
 }
}

package com.kai.soundgood.aigeq

/** Production mixer writes remain disabled until OSC mappings and hardware are verified. */
data class FixProposal(val target:String,val tool:String,val cutDb:Double,val confirmed:Boolean)
object FoHSafetyPolicy {
 const val HARDWARE_WRITES_ENABLED=false
 fun allow(p:FixProposal,verifiedSource:Boolean,completeSnapshot:Boolean):Boolean =
  HARDWARE_WRITES_ENABLED && p.confirmed && verifiedSource && completeSnapshot &&
  p.cutDb in -3.0..0.0 && p.tool in setOf("PEQ","GEQ")
}

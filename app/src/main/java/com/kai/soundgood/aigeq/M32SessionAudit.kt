package com.kai.soundgood.aigeq

/** Never interpret a UDP reply as proof of FX insert routing. */
data class M32SessionEvidence(val oscReply:Boolean,val sourceAudioVerified:Boolean,val eqSnapshotComplete:Boolean,val fxRoutingVerified:Boolean)
data class M32SessionReport(val canAnalyze:Boolean,val canProposePeq:Boolean,val canProposeGeq:Boolean,val canWrite:Boolean,val reasons:List<String>)
object M32SessionAudit {
 fun evaluate(e:M32SessionEvidence):M32SessionReport {
  val reasons=buildList {
   if(!e.oscReply)add("No M32 OSC reply")
   if(!e.sourceAudioVerified)add("Selected CH/Bus has no verified audio capture path")
   if(!e.eqSnapshotComplete)add("EQ snapshot incomplete")
   if(!e.fxRoutingVerified)add("FX/GEQ routing unverified")
  }
  return M32SessionReport(e.sourceAudioVerified,e.oscReply&&e.sourceAudioVerified&&e.eqSnapshotComplete,
   e.oscReply&&e.sourceAudioVerified&&e.eqSnapshotComplete&&e.fxRoutingVerified,false,reasons)
 }
}

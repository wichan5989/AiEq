package com.kai.soundgood.aigeq
fun main() {
 check(!M32SessionAudit.evaluate(M32SessionEvidence(true,false,true,true)).canAnalyze)
 check(!M32SessionAudit.evaluate(M32SessionEvidence(true,true,true,false)).canProposeGeq)
 check(M32SessionAudit.evaluate(M32SessionEvidence(true,true,true,true)).canProposePeq)
 check(!FoHSafetyPolicy.allow(FixProposal("BUS01","PEQ",-2.0,true),true,true))
 val pipeline=LiveFeedbackPipeline()
 val frame=AudioFrame(ShortArray(4096){0},48000,0,CaptureKind.ANDROID_MIC)
 check(pipeline.ingest(frame)!=null)
 check(pipeline.stablePeaks().isEmpty())
 println("v3.0 smoke tests PASS (6 assertions)")
}

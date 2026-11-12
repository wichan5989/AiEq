package com.kai.soundgood.aigeq

/** A cross-platform assist state machine: never grants itself hardware write authority. */
enum class AssistPhase { IDLE, OBSERVING, SUGGESTED, WAITING_FOR_OPERATOR, VERIFYING, COMPLETED, ABORTED }
data class AssistSession(val phase:AssistPhase,val advice:FeedbackAdvice?,val message:String)
class FeedbackAssistSession(private val engine:AiFeedbackEngine=AiFeedbackEngine()) {
 fun analyze(evidence:FeedbackEvidence, capabilities:MixerCapabilities, sourceLocked:Boolean):AssistSession {
  if(!sourceLocked) return AssistSession(AssistPhase.OBSERVING,null,"Lock and verify RTA source before suggesting a mixer change")
  if(evidence.source==EvidenceSource.SIMULATED) return AssistSession(AssistPhase.OBSERVING,null,"Simulated evidence cannot authorize mixer changes")
  if(!evidence.frequencyHz.isFinite() || evidence.frequencyHz !in 20.0..20000.0 || !evidence.prominenceDb.isFinite()) return AssistSession(AssistPhase.ABORTED,null,"Invalid analysis evidence")
  val advice=engine.advise(evidence,capabilities)
  return if(advice.tool==FixTool.OBSERVE) AssistSession(AssistPhase.OBSERVING,advice,advice.explanation)
   else AssistSession(AssistPhase.WAITING_FOR_OPERATOR,advice,"Operator must inspect target, band and existing EQ before any action")
 }
 fun verify(before:List<VerificationSample>,after:List<VerificationSample>,frequencyHz:Double):AssistSession {
  val result=FeedbackVerifier().compare(before,after,frequencyHz)
  return AssistSession(if(result.status==VerificationStatus.IMPROVED) AssistPhase.COMPLETED else AssistPhase.OBSERVING,null,result.message+"; reduction ${"%.1f".format(result.reductionDb)} dB")
 }
}

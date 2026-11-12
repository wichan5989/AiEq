package com.kai.soundgood.aigeq

/** Explicitly disabled hardware write boundary until OSC maps and console readback pass hardware tests. */
data class OperatorApproval(val target: EqTarget, val tool: EqKind, val band:Int, val approved:Boolean, val timeMs:Long)
data class WriteOutcome(val executed:Boolean,val reason:String)
interface EqHardwareWriter { fun apply(plan: EqPlanResult.Accepted, approval:OperatorApproval):WriteOutcome }
class LockedM32Writer:EqHardwareWriter {
 override fun apply(plan:EqPlanResult.Accepted,approval:OperatorApproval):WriteOutcome =
  WriteOutcome(false,"LIVE WRITES LOCKED: verified OSC scaling, FX routing, fresh readback and hardware test required")
}
class SafeWriterCoordinator(private val planner:SafeEqPlanner=SafeEqPlanner(),private val writer:EqHardwareWriter=LockedM32Writer()) {
 fun submit(plan:EqPlan, caps:EqCapabilities, approval:OperatorApproval,nowMs:Long):WriteOutcome {
  if(!approval.approved || approval.target!=plan.key.target || approval.tool!=plan.key.tool || approval.band!=plan.key.band) return WriteOutcome(false,"Approval mismatch")
  if(nowMs<approval.timeMs || nowMs-approval.timeMs>15000) return WriteOutcome(false,"Approval expired")
  val validated=planner.validate(plan,caps)
  if(validated !is EqPlanResult.Accepted) return WriteOutcome(false,(validated as EqPlanResult.Rejected).reason)
  return writer.apply(validated,approval)
 }
}

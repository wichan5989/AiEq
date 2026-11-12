package com.kai.soundgood.aigeq

/** Manual PEQ gain only. X32/M32 OSC gain uses normalized 0..1 for -15..+15 dB.
 * Never used by automatic feedback detection. */
class M32ManualPeqWriter(private val osc: OscClient) {
 fun writeGainAndVerify(target: EqTarget, band: Int, gainDb: Float): Boolean {
  require(M32TargetValidation.valid(target))
  require(band in 1..target.peqBands)
  require(gainDb in -3f..0f) { "Initial live-write limit is -3..0 dB" }
  val prefix=when(target.kind) {
   TargetKind.CHANNEL -> "/ch/%02d/eq".format(target.number)
   TargetKind.BUS -> "/bus/%02d/eq".format(target.number)
   TargetKind.MAIN_LR -> "/main/st/eq"
   TargetKind.MATRIX -> "/mtx/%02d/eq".format(target.number)
  }
  val path="$prefix/$band/g"
  osc.sendQuery(path)
  val baseline=osc.receive()?.let(OscReplyDecoder::decode)
  require(baseline?.address==path && baseline.arguments.firstOrNull() is Float) { "No verified baseline response" }
  val normalized=(gainDb+15f)/30f
  osc.sendFloat(path,normalized)
  osc.sendQuery(path)
  val reply=osc.receive()?.let(OscReplyDecoder::decode)
  return reply?.address==path && ((reply.arguments.firstOrNull() as? Float)?.let { kotlin.math.abs(it-normalized)<0.002f } == true)
 }
}

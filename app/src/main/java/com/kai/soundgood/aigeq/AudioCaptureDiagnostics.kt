package com.kai.soundgood.aigeq

/** Pure capture health monitor. Call on the capture worker thread. */
data class CaptureHealth(val samples:Long, val blocks:Long, val shortReads:Long, val errors:Long,
 val silentBlocks:Long, val clippedBlocks:Long, val rmsDbfs:Double, val peakDbfs:Double,
 val lastAudioAtMs:Long, val lastError:Int?) {
 fun stalled(nowMs:Long, thresholdMs:Long=1500):Boolean = blocks>0 && nowMs-lastAudioAtMs>thresholdMs
}
class AudioCaptureDiagnostics {
 private var samples=0L; private var blocks=0L; private var shortReads=0L
 private var errors=0L; private var silent=0L; private var clipped=0L
 private var lastAudioAt=0L; private var lastError:Int?=null
 private var rms=-120.0; private var peak=-120.0
 fun accept(data:ShortArray, count:Int, requested:Int, nowMs:Long):CaptureHealth {
  if(count<=0) { errors++;lastError=count;return snapshot() }
  if(count<requested) shortReads++
  var sum=0.0;var maximum=0
  for(i in 0 until count) { val v=data[i].toInt();sum+=v.toDouble()*v; maximum=maxOf(maximum,kotlin.math.abs(v)) }
  rms=20*kotlin.math.log10((kotlin.math.sqrt(sum/count)/32768.0).coerceAtLeast(1e-6))
  peak=20*kotlin.math.log10((maximum/32768.0).coerceAtLeast(1e-6))
  if(maximum<33) silent++
  if(maximum>=32760) clipped++
  samples+=count;blocks++;lastAudioAt=nowMs;lastError=null
  return snapshot()
 }
 fun snapshot()=CaptureHealth(samples,blocks,shortReads,errors,silent,clipped,rms,peak,lastAudioAt,lastError)
}

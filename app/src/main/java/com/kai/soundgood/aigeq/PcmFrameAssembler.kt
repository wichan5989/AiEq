package com.kai.soundgood.aigeq

/** Reassembles arbitrary PCM16 reads into exact FFT windows without zero padding. */
class PcmFrameAssembler(private val frameSize:Int=4096) {
 init { require(frameSize>0) }
 private val pending=ShortArray(frameSize)
 private var used=0
 var emittedFrames:Long=0; private set
 fun reset() { used=0 }
 fun pendingSamples():Int=used
 fun append(samples:ShortArray,count:Int,onFrame:(ShortArray)->Unit) {
  require(count in 0..samples.size)
  var offset=0
  while(offset<count) {
   val take=minOf(frameSize-used,count-offset)
   samples.copyInto(pending,used,offset,offset+take)
   used+=take;offset+=take
   if(used==frameSize) {
    onFrame(pending.copyOf())
    emittedFrames++
    used=0
   }
  }
 }
}

/** Monotonic clock based monitoring; silence is not the same as capture stall. */
class CaptureStallWatch(private val timeoutMs:Long=1500) {
 init { require(timeoutMs>0) }
 private var lastReadMs:Long?=null
 fun onRead(nowMs:Long) { lastReadMs=nowMs }
 fun stalled(nowMs:Long):Boolean=lastReadMs?.let { nowMs-it>timeoutMs } ?: false
 fun reset() { lastReadMs=null }
}

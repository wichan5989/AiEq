package com.kai.soundgood.aigeq

/** Local envelope only. No OSC transport or mixer gain encoding. */
internal class ManualDynamicEnvelope {
 private var depth=0f
 private var lastActive:Long?=null
 fun reset(){depth=0f;lastActive=null}
 fun step(now:Long,dt:Long,active:Boolean,maxCut:Float,attack:Int,hold:Int,release:Int):Float {
  require(maxCut.isFinite() && maxCut in 0f..3f && attack>0 && hold>=0 && release>0)
  val elapsed=dt.coerceIn(0L,100L).toFloat()
  if(active) {
   lastActive=now
   depth=(depth+maxCut*elapsed/attack).coerceAtMost(maxCut)
  } else if(lastActive?.let {now-it>=hold} != false) {
   depth=(depth-maxCut*elapsed/release).coerceAtLeast(0f)
  }
  depth=depth.coerceIn(0f,maxCut)
  return -depth
 }
}

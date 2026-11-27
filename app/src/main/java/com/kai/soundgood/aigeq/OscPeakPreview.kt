package com.kai.soundgood.aigeq
import kotlin.math.*
/** Tonal candidate only; not proof of acoustic feedback. */
class OscPeakPreview {
 private var bin=-1
 private var since=0L
 private var last=0L
 fun reset(){bin=-1;since=0;last=0}
 fun analyze(levels:List<Float>,now:Long,threshold:Float):String? {
  if(levels.size!=100 || levels.any{!it.isFinite()}){reset();return null}
  if(now-last>500) reset()
  last=now
  val i=(20..90).maxByOrNull { levels[it] } ?: return null
  val neighbors=(i-4..i+4).filter{abs(it-i)>=2}.map{levels[it]}
  val prominence=levels[i]-neighbors.average()
  if(levels[i]<-65f || prominence<threshold.coerceAtLeast(10f)){reset();return null}
  if(bin<0 || abs(i-bin)>1){bin=i;since=now;return null}
  if(now-since<1000)return null
  val hz=(20.0*2.0.pow(i/10.0)).roundToInt()
  return "TONAL PEAK ~${hz} Hz · +${prominence.roundToInt()} dB · preview −1 dB"
 }
}

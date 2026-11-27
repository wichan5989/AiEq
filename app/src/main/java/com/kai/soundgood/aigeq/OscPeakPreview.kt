package com.kai.soundgood.aigeq
import kotlin.math.*
/** Tonal candidate only; not proof of acoustic feedback. */
class OscPeakPreview {
 private var bin=-1
 private var since=0L
 private var last=0L
 fun reset(){bin=-1;since=0;last=0}
 fun analyze(levels:List<Float>,now:Long,threshold:Float,holdMs:Long=1200):String? {
  if(levels.size!=100 || levels.any{!it.isFinite()}){reset();return null}
  if(now-last>500) reset()
  last=now
  fun prominence(i:Int)=levels[i]-(i-4..i+4).filter{abs(it-i)>=2}.map{levels[it]}.average()
  val candidates=(20..90).filter { i ->
   levels[i]>=-65f && levels[i]>=levels[i-1] && levels[i]>=levels[i+1] &&
   prominence(i)>=threshold.coerceIn(8f,24f) &&
   levels[i]-max(levels[i-2],levels[i+2])>=5f
  }
  val i=candidates.maxByOrNull { prominence(it) } ?: run {reset();return null}
  val prominence=prominence(i)
  // Reject strong harmonic companions: common in sustained musical notes.
  val harmonicBins=listOf(i+10,i+16,i+20).filter{it<=98}
  if(harmonicBins.any { h -> (h-1..h+1).maxOf{levels[it]}>=levels[i]-6f }) {reset();return null}
  if(bin<0 || abs(i-bin)>1){bin=i;since=now;return null}
  if(now-since<holdMs.coerceIn(700L,2500L))return null
  val hz=(20.0*2.0.pow(i/10.0)).roundToInt()
  return "TONAL PEAK ~${hz} Hz · +${prominence.roundToInt()} dB · preview −1 dB"
 }
}

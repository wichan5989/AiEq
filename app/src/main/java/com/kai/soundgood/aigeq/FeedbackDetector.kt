package com.kai.soundgood.aigeq

import kotlin.math.*

data class Candidate(val hz: Double, val prominenceDb: Double, val persistenceMs: Long, val suggestedCutDb: Double)
/** Hann-window FFT peak interpolation; tonal candidate detector, not proof of acoustic feedback. */
class FeedbackDetector(private val sampleRate: Int = 48000) {
 val centers = doubleArrayOf(20.0,25.0,31.5,40.0,50.0,63.0,80.0,100.0,125.0,160.0,200.0,250.0,315.0,400.0,500.0,630.0,800.0,1000.0,1250.0,1600.0,2000.0,2500.0,3150.0,4000.0,5000.0,6300.0,8000.0,10000.0,12500.0,16000.0,20000.0)
 private var previousHz = 0.0
 private var startMs = 0L
 private var lastMs = 0L
 @Synchronized fun reset() { previousHz=0.0; startMs=0L; lastMs=0L }
 @Synchronized fun analyze(samples: ShortArray, nowMs: Long, thresholdDb: Double = 10.0): Candidate? {
  val n=samples.size
  if(n<1024 || n>16384 || (n and (n-1))!=0) return null
  val rms=sqrt(samples.sumOf { it.toDouble()*it.toDouble() }/n)/32768.0
  if(rms<0.003 || samples.any { abs(it.toInt())>=32760 }) { reset(); return null }
  val re=DoubleArray(n) { i -> samples[i]*(0.5-0.5*cos(2*PI*i/(n-1))) }
  val im=DoubleArray(n)
  var j=0
  for(i in 1 until n) {
   var bit=n shr 1
   while((j and bit)!=0) { j=j xor bit; bit=bit shr 1 }
   j=j xor bit
   if(i<j) { val t=re[i]; re[i]=re[j]; re[j]=t }
  }
  var len=2
  while(len<=n) {
   val angle=-2*PI/len
   for(base in 0 until n step len) {
    for(k in 0 until len/2) {
     val a=base+k; val b=a+len/2
     val c=cos(angle*k); val d=sin(angle*k)
     val tr=re[b]*c-im[b]*d; val ti=re[b]*d+im[b]*c
     re[b]=re[a]-tr; im[b]=im[a]-ti
     re[a]+=tr; im[a]+=ti
    }
   }
   len*=2
  }
  val p=DoubleArray(n/2) { re[it]*re[it]+im[it]*im[it]+1e-12 }
  val binHz=sampleRate.toDouble()/n
  val lo=max(7,ceil(80/binHz).toInt())
  val hi=min(p.size-8,floor(12000/binHz).toInt())
  val peak=(lo..hi).filter { p[it]>p[it-1] && p[it]>=p[it+1] }.maxByOrNull { p[it] }
  if(peak==null) { reset(); return null }
  val background=(-7..7).filter { abs(it)>=3 }.map { p[peak+it] }.average()
  val prominence=10*log10(p[peak]/background)
  if(prominence<thresholdDb.coerceIn(6.0,24.0)) { reset(); return null }
  val a=ln(p[peak-1]); val b=ln(p[peak]); val c=ln(p[peak+1])
  val denom=a-2*b+c
  val offset=if(abs(denom)>1e-9) (0.5*(a-c)/denom).coerceIn(-0.5,0.5) else 0.0
  val hz=(peak+offset)*binHz
  if(previousHz==0.0 || abs(hz-previousHz)>max(binHz,previousHz*0.005) || nowMs-lastMs>250 || nowMs<lastMs) startMs=nowMs
  previousHz=hz; lastMs=nowMs
  val elapsed=nowMs-startMs
  return if(elapsed>=700) Candidate(round(hz),prominence,elapsed,-1.0) else null
 }
}

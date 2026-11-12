package com.kai.soundgood.aigeq

import kotlin.math.*

data class Candidate(val hz: Double, val prominenceDb: Double, val persistenceMs: Long, val suggestedCutDb: Double)
/** Windowed Goertzel analysis over standard 1/3 octave centers; conservative prototype, not calibrated. */
class FeedbackDetector(private val sampleRate: Int = 48000) {
 val centers = doubleArrayOf(20.0,25.0,31.5,40.0,50.0,63.0,80.0,100.0,125.0,160.0,200.0,250.0,315.0,400.0,500.0,630.0,800.0,1000.0,1250.0,1600.0,2000.0,2500.0,3150.0,4000.0,5000.0,6300.0,8000.0,10000.0,12500.0,16000.0,20000.0)
 private var previous = -1
 private var startMs = 0L
 fun analyze(samples: ShortArray, nowMs: Long): Candidate? {
  if (samples.size < 1024) return null
  val powers = centers.map { freq ->
   val omega = 2.0 * PI * freq / sampleRate
   val coefficient = 2.0 * cos(omega)
   var q1 = 0.0; var q2 = 0.0
   for (i in samples.indices) {
    val window = 0.5 - 0.5 * cos(2.0 * PI * i / (samples.size - 1))
    val q0 = samples[i] * window + coefficient*q1-q2
    q2=q1; q1=q0
   }
   max(1e-9,q1*q1+q2*q2-coefficient*q1*q2)
  }
  val best = (2 until centers.size-2).maxByOrNull { powers[it] / max(1e-9, (powers[it-2]+powers[it-1]+powers[it+1]+powers[it+2])/4) } ?: return null
  val neighbors = (powers[best-2]+powers[best-1]+powers[best+1]+powers[best+2])/4
  val prominence = 10*log10(powers[best]/max(1e-9,neighbors))
  if (prominence < 10.0) { previous=-1; return null }
  if (previous != best) { previous=best; startMs=nowMs }
  val elapsed=nowMs-startMs
  return if (elapsed >= 700) Candidate(centers[best],prominence,elapsed,-1.0) else null
 }
}

package com.kai.soundgood.aigeq
import java.util.ArrayDeque

/** Spectrum analysis from verified PCM, independent of mixer control protocol. */
class LiveFeedbackPipeline(private val pipeline:RtaAudioPipeline=RtaAudioPipeline()) {
 private val recent=ArrayDeque<SpectrumFrame>()
 fun ingest(frame:AudioFrame):SpectrumFrame? {
  val spectrum=pipeline.process(frame) ?: return null
  recent.addLast(spectrum)
  while(recent.size>20)recent.removeFirst()
  return spectrum
 }
 fun stablePeaks(minFrames:Int=8,prominenceDb:Double=10.0):List<Pair<Double,Double>> {
  if(recent.size<minFrames)return emptyList()
  val frames=recent.toList().takeLast(minFrames)
  val last=frames.last()
  return (3 until last.binsDbfs.size-3).mapNotNull { i ->
   val avg=frames.map{it.binsDbfs[i]}.average()
   val neighbors=frames.map{(it.binsDbfs[i-3]+it.binsDbfs[i+3])/2}.average()
   val stable=frames.all{it.binsDbfs[i]>neighbors-3}
   if(stable&&avg-neighbors>=prominenceDb) i*last.binHz to avg else null
  }.sortedByDescending{it.second}.take(8)
 }
}

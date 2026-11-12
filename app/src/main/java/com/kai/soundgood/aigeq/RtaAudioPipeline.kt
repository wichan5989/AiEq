package com.kai.soundgood.aigeq

import kotlin.math.*
/** Audio frames must originate from a verified capture path; OSC control packets are not PCM. */
enum class CaptureKind { ANDROID_MIC, USB_INTERFACE, MIXER_RTA_VERIFIED }
data class AudioFrame(val pcm: ShortArray, val sampleRate: Int, val timestampMs: Long, val source: CaptureKind)
data class SpectrumFrame(val binsDbfs: DoubleArray, val binHz: Double, val timestampMs: Long, val source: CaptureKind)
class RtaAudioPipeline(private val fftSize:Int=4096) {
 init { require(fftSize in 256..16384 && fftSize.countOneBits()==1) }
 fun process(frame: AudioFrame):SpectrumFrame? {
  if(frame.sampleRate !in 8000..192000 || frame.pcm.size<fftSize) return null
  val re=DoubleArray(fftSize);val im=DoubleArray(fftSize)
  for(i in 0 until fftSize) re[i]=frame.pcm[i]/32768.0*(0.5-0.5*cos(2*PI*i/(fftSize-1)))
  var j=0
  for(i in 1 until fftSize){var bit=fftSize shr 1;while(j and bit !=0){j=j xor bit;bit=bit shr 1};j=j xor bit;if(i<j){val tmp=re[i];re[i]=re[j];re[j]=tmp}}
  var len=2
  while(len<=fftSize){val theta=-2*PI/len;val wr=cos(theta);val wi=sin(theta)
   for(start in 0 until fftSize step len){var cr=1.0;var ci=0.0
    for(k in 0 until len/2){val a=start+k;val b=a+len/2;val tr=cr*re[b]-ci*im[b];val ti=cr*im[b]+ci*re[b];re[b]=re[a]-tr;im[b]=im[a]-ti;re[a]+=tr;im[a]+=ti;val nr=cr*wr-ci*wi;ci=cr*wi+ci*wr;cr=nr}
   };len=len shl 1
  }
  val bins=DoubleArray(fftSize/2+1){i->20*log10(max(1e-12,hypot(re[i],im[i])/(fftSize*0.25)))}
  return SpectrumFrame(bins,frame.sampleRate.toDouble()/fftSize,frame.timestampMs,frame.source)
 }
}

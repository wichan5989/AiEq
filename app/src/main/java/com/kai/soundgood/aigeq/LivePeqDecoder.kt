package com.kai.soundgood.aigeq

import kotlin.math.pow

data class DecodedPeqBand(val number:Int,val type:Int,val hz:Double,val gainDb:Double,val q:Double)
object LivePeqDecoder {
 val types=listOf("LCut","LShv","PEQ","VEQ","HShv","HCut","BU6","BU12","BS12","LR12","BU18","BU24","BS24","LR24")
 fun decode(snapshot:M32PeqSnapshot):List<DecodedPeqBand> {
  if(!snapshot.complete) return emptyList()
  fun value(suffix:String)=snapshot.values.entries.firstOrNull { it.key.endsWith(suffix) }?.value?.singleOrNull()
  fun normalized(suffix:String):Double?=(value(suffix) as? Float)?.toDouble()?.takeIf { it.isFinite() && it in 0.0..1.0 }
  return (1..snapshot.target.peqBands).mapNotNull { b ->
   val type=(value("/$b/type") as? Int)?.takeIf { it in types.indices } ?: return@mapNotNull null
   val f=normalized("/$b/f") ?: return@mapNotNull null
   val g=normalized("/$b/g") ?: return@mapNotNull null
   val q=normalized("/$b/q") ?: return@mapNotNull null
   DecodedPeqBand(b,type,20.0*1000.0.pow(f),30.0*g-15.0,10.0*0.03.pow(q))
  }
 }
}

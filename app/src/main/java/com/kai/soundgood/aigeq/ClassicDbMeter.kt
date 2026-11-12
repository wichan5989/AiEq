package com.kai.soundgood.aigeq

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/** Digital input meter: dBFS, not calibrated acoustic dB SPL. */
@Composable
fun ClassicDbMeter(rmsDbfs: Double, peakDbfs: Double, minDbfs: Double?, maxDbfs: Double?, onReset: () -> Unit, modifier: Modifier = Modifier) {
 val gold = Color(0xFFD9AA59)
 val fg = Color(0xFFE6E0D3)
 Column(modifier.background(Color(0xFF20242A), RoundedCornerShape(8.dp)).padding(8.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
   Text("CLASSIC VU  /  INPUT",color=gold,fontSize=12.sp,fontWeight=FontWeight.Bold)
   Text("dBFS",color=fg,fontSize=11.sp)
  }
  Canvas(Modifier.fillMaxWidth().height(90.dp)) {
   val center=Offset(size.width/2f,size.height*0.93f)
   val radius=minOf(size.width*0.40f,size.height*0.82f)
   val start=205.0
   val sweep=130.0
   for(i in 0..12) {
    val angle=(start + i*sweep/12.0)*PI/180.0
    val outer=Offset(center.x+(radius*cos(angle)).toFloat(),center.y+(radius*sin(angle)).toFloat())
    val inner=Offset(center.x+((radius- if(i%3==0) 13f else 7f)*cos(angle)).toFloat(),center.y+((radius- if(i%3==0) 13f else 7f)*sin(angle)).toFloat())
    drawLine(if(i>=10) Color(0xFFE66A56) else gold,inner,outer,strokeWidth=if(i%3==0) 2.5f else 1.5f)
   }
   val normalized=((rmsDbfs.coerceIn(-60.0,0.0)+60.0)/60.0)
   val angle=(start+normalized*sweep)*PI/180.0
   val tip=Offset(center.x+(radius*0.88*cos(angle)).toFloat(),center.y+(radius*0.88*sin(angle)).toFloat())
   drawLine(Color(0xFFF5F0E8),center,tip,strokeWidth=3f)
   drawCircle(gold,5f,center)
  }
  Text("RMS ${"%.1f".format(rmsDbfs)}   PEAK ${"%.1f".format(peakDbfs)}",color=fg,fontSize=13.sp,fontWeight=FontWeight.Bold)
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
   Text("MIN ${minDbfs?.let { "%.1f".format(it) } ?: "--"}",color=Color(0xFF68D8B1),fontSize=12.sp)
   Text("MAX ${maxDbfs?.let { "%.1f".format(it) } ?: "--"}",color=Color(0xFFE6A66A),fontSize=12.sp)
   OutlinedButton(onClick=onReset,contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp),modifier=Modifier.height(30.dp)) {Text("RESET",fontSize=10.sp)}
  }
  Text("RMS MIN/MAX since reset · -60 to 0 dBFS",color=Color(0xFF9CA3AF),fontSize=10.sp)
 }
}

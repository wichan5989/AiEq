package com.kai.soundgood.aigeq

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.*

enum class RtaMode { BAR, LINE, AREA, SPECTROGRAM }
enum class RtaStyle { PIXEL_LED, SMOOTH }

/** dBFS relative to full-scale digital input, not acoustic dB SPL. */
class RtaAnalyzer(private val sampleRate: Int = 48000) {
 val centers = FeedbackDetector().centers
 fun measure(samples: ShortArray): List<Float> = centers.map { frequency ->
  val omega=2.0*PI*frequency/sampleRate
  val coeff=2.0*cos(omega)
  var q1=0.0; var q2=0.0
  for(i in samples.indices) {
   val w=0.5-0.5*cos(2*PI*i/(samples.size-1))
   val q0=samples[i].toDouble()/32768.0*w+coeff*q1-q2
   q2=q1; q1=q0
  }
  val magnitude=sqrt(max(0.0,q1*q1+q2*q2-coeff*q1*q2))*4.0/samples.size
  (20*log10(max(1e-8,magnitude))).toFloat().coerceIn(-120f,0f)
 }
}

private val cyan=Color(0xFF13BFF2)
private val green=Color(0xFF34D87A)
private val yellow=Color(0xFFF3E35B)
private val orange=Color(0xFFFF9738)
private val red=Color(0xFFF04445)
private fun heat(db:Float, floor:Float):Color {
 val t=((db-floor)/(0f-floor)).coerceIn(0f,1f)
 val stops=listOf(cyan,green,yellow,orange,red)
 val scaled=t*4
 val index=scaled.toInt().coerceIn(0,3)
 return androidx.compose.ui.graphics.lerp(stops[index],stops[index+1],scaled-index)
}

@Composable fun RtaDisplay(levels:List<Float>, history:List<List<Float>>, mode:RtaMode, floorDb:Float, style:RtaStyle, modifier:Modifier=Modifier) {
 Canvas(modifier.background(Color(0xFF151B21),RoundedCornerShape(8.dp))) {
  val count=31; val dx=size.width/count; val height=size.height
  for(i in 0..4) {
   val y=i*height/4
   drawLine(Color(0xFF34404A),Offset(0f,y),Offset(size.width,y),1f)
  }
  fun y(db:Float)=height*(1f-((db-floorDb)/(0f-floorDb)).coerceIn(0f,1f))
  val led = style == RtaStyle.PIXEL_LED
  val ledRows = 24
  val cellHeight = height / ledRows
  fun drawLedColumn(index:Int, level:Float, alpha:Float=1f) {
   val fraction=((level-floorDb)/(-floorDb)).coerceIn(0f,1f)
   for(j in 0 until ledRows) {
    val t=(j+.5f)/ledRows
    if(t <= fraction) {
     drawRect(heat(floorDb+t*(-floorDb),floorDb).copy(alpha=alpha),
      Offset(index*dx+dx*.12f,height-(j+1)*cellHeight+1.5f),
      androidx.compose.ui.geometry.Size(dx*.76f,(cellHeight-3f).coerceAtLeast(1f)))
    }
   }
  }
  when(mode) {
   RtaMode.BAR -> for(i in 0 until min(count,levels.size)) {
    if(led) drawLedColumn(i,levels[i]) else {
     val yy=y(levels[i]); val barHeight=(height-yy).coerceAtLeast(0f)
     val steps=48
     for(j in 0 until steps) {
      val top=yy+barHeight*j/steps
      val bottom=yy+barHeight*(j+1)/steps
      val db=floorDb+(1f-top/height)*(-floorDb)
      drawRect(heat(db,floorDb),Offset(i*dx+dx*.12f,top),
       androidx.compose.ui.geometry.Size(dx*.76f,bottom-top+1f))
     }
    }
   }
   RtaMode.LINE, RtaMode.AREA -> {
    val points=levels.take(count).mapIndexed { i,v -> Offset((i+.5f)*dx,y(v)) }
    if(mode==RtaMode.AREA) {
     if(led) for(i in 0 until min(count,levels.size)) drawLedColumn(i,levels[i],.7f)
     else for(i in points.indices) {
      val p=points[i]; val steps=48
      for(j in 0 until steps) {
       val top=p.y+(height-p.y)*j/steps
       val bottom=p.y+(height-p.y)*(j+1)/steps
       val db=floorDb+(1f-top/height)*(-floorDb)
       drawRect(heat(db,floorDb).copy(alpha=.55f),Offset(i*dx,top),
        androidx.compose.ui.geometry.Size(dx+1f,bottom-top+1f))
      }
     }
    }
    if(led) {
     // Discrete pixel points rather than a continuous line.
     for(i in points.indices) {
      val p=points[i]
      val snapped=(p.y/cellHeight).toInt().coerceIn(0,ledRows-1)*cellHeight
      drawRect(heat(levels[i],floorDb),Offset(p.x-dx*.28f,snapped+1.5f),
       androidx.compose.ui.geometry.Size(dx*.56f,(cellHeight-3f).coerceAtLeast(1f)))
     }
    } else for(i in 1 until points.size) {
     val a=points[i-1]; val b=points[i]
     for(j in 0 until 12) {
      val t=j/12f; val u=(j+1)/12f
      val p=Offset(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t)
      val q=Offset(a.x+(b.x-a.x)*u,a.y+(b.y-a.y)*u)
      val db=floorDb+(1f-((p.y+q.y)/2)/height)*(-floorDb)
      drawLine(heat(db,floorDb),p,q,3f)
     }
    }
   }
   RtaMode.SPECTROGRAM -> {
    val rows=history.size.coerceAtLeast(1)
    history.forEachIndexed { row, frame ->
     frame.take(count).forEachIndexed { i,db ->
      val gap=if(led) 1.5f else 0f
      drawRect(heat(db,floorDb),Offset(i*dx+gap,row*height/rows+gap),
       androidx.compose.ui.geometry.Size((dx-gap*2+1).coerceAtLeast(1f),
        (height/rows-gap*2+1).coerceAtLeast(1f)))
     }
    }
   }
  }
 }
}

/** 31 standard third-octave band centers; labels align with the 31 RTA columns. */
@Composable fun RtaFrequencyAxis(modifier:Modifier=Modifier) {
 val labels=mapOf(0 to "20",3 to "40",6 to "80",9 to "160",12 to "315",15 to "630",18 to "1.25k",21 to "2.5k",24 to "5k",27 to "10k",30 to "20k")
 Column(modifier) {
  Row(modifier=Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
   labels.forEach { (_,label) ->
    Text(label,color=Color.LightGray,style=MaterialTheme.typography.labelSmall,maxLines=1)
   }
  }
  Text("Frequency (Hz) · 31 bands, 20 Hz–20 kHz",color=Color.Gray,style=MaterialTheme.typography.labelSmall)
 }
}

@Composable fun RtaControls(mode:RtaMode,onMode:(RtaMode)->Unit,floorDb:Float,onFloor:(Float)->Unit,style:RtaStyle,onStyle:(RtaStyle)->Unit) {
 Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Text("RTA DISPLAY",color=Color(0xFFD9AA59))
  Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
   RtaMode.entries.forEach { item ->
    FilterChip(selected=mode==item,onClick={onMode(item)},label={Text(item.name.lowercase().replaceFirstChar { it.uppercase() })},modifier=Modifier.weight(1f))
   }
  }
  Text("VISUAL STYLE",color=Color(0xFFD9AA59))
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
   RtaStyle.entries.forEach { item ->
    FilterChip(selected=style==item,onClick={onStyle(item)},
     label={Text(if(item==RtaStyle.PIXEL_LED) "Pixel LED" else "Smooth")},modifier=Modifier.weight(1f))
   }
  }
  Text("RTA range: ${floorDb.toInt()} to 0 dBFS",color=Color.LightGray)
  Slider(value=floorDb,onValueChange=onFloor,valueRange=-120f..-30f,steps=17)
  Row(horizontalArrangement=Arrangement.SpaceBetween,modifier=Modifier.fillMaxWidth()) {
   Text("Blue · low",color=cyan); Text("Green",color=green); Text("Yellow",color=yellow); Text("Orange",color=orange); Text("Red · high",color=red)
  }
  Text("dBFS is relative to digital full scale; this is not calibrated dB SPL.",color=Color.Gray,style=MaterialTheme.typography.labelSmall)
 }
}

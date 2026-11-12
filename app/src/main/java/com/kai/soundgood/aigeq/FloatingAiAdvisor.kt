package com.kai.soundgood.aigeq

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/** Overlay on top of the EQ screen. All actions are local previews; no OSC write path. */
@Composable
fun androidx.compose.foundation.layout.BoxScope.FloatingAiAdvisor(
 mode: AiOperatingMode,
 expanded: Boolean,
 points: List<FeedbackSuggestion>,
 currentId: Long?,
 detectedHz: Double?,
 onExpand: () -> Unit,
 onHide: () -> Unit,
 onMode: (AiOperatingMode) -> Unit,
 onSelect: (Long) -> Unit,
 onReview: (Long) -> Unit,
 onConfirm: (Long) -> Unit,
 onIgnore: (Long) -> Unit,
 onUndo: (Long) -> Unit,
 onNext: () -> Unit
) {
 val current=points.firstOrNull { it.id==currentId } ?: points.firstOrNull()
 val density = LocalDensity.current
 var x by remember { mutableFloatStateOf(0f) }
 var y by remember { mutableFloatStateOf(0f) }
 var overlayWidth by remember { mutableIntStateOf(0) }
 var overlayHeight by remember { mutableIntStateOf(0) }
 // Initial position is top-right. x is the leftward drag distance from the right edge.
 // Constrain the floating window inside the host viewport; no EQ resize.
 BoxWithConstraints(Modifier.fillMaxSize()) {
  val maxWidthPx=with(density) { maxWidth.toPx() }
  val maxHeightPx=with(density) { maxHeight.toPx() }
  val maxX=(maxWidthPx-overlayWidth-8f).coerceAtLeast(0f)
  val maxY=(maxHeightPx-overlayHeight-8f).coerceAtLeast(0f)
  LaunchedEffect(maxX,maxY) { x=x.coerceIn(0f,maxX); y=y.coerceIn(0f,maxY) }
  Surface(
  modifier=Modifier.align(Alignment.TopEnd).offset { IntOffset((-x).roundToInt(),y.roundToInt()) }
   .padding(top=8.dp,start=8.dp).widthIn(max=280.dp).fillMaxWidth(0.78f)
   .onSizeChanged { overlayWidth=it.width; overlayHeight=it.height }.zIndex(5f),
  color=Color(0xF51C242D),shape=RoundedCornerShape(12.dp),
  tonalElevation=8.dp,shadowElevation=8.dp
 ) {
  Column(Modifier.padding(9.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
   Row(modifier=Modifier.pointerInput(maxX,maxY) {
      detectDragGestures { change,drag ->
       change.consume()
       x=(x-drag.x).coerceIn(0f,maxX)
       y=(y+drag.y).coerceIn(0f,maxY)
      }
    },verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
    Text("AI ADVISOR (${points.count { it.status==SuggestionStatus.PENDING || it.status==SuggestionStatus.REVIEWED }})",color=Color(0xFFE3BA70),modifier=Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
    TextButton(onClick=onExpand,contentPadding=PaddingValues(2.dp)) { Text(if(expanded) "−" else "+") }
    TextButton(onClick=onHide,contentPadding=PaddingValues(2.dp)) { Text("×") }
   }
   if(expanded) {
    Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
     FilterChip(selected=mode==AiOperatingMode.MONITOR_ONLY,onClick={onMode(AiOperatingMode.MONITOR_ONLY)},label={Text("MONITOR")})
     FilterChip(selected=mode==AiOperatingMode.ASSIST_CONFIRM,onClick={onMode(AiOperatingMode.ASSIST_CONFIRM)},label={Text("ASSIST")})
    }
    if(current==null) {
     Text("No queued suggestions",color=Color.LightGray)
     Text("Detected: ${detectedHz?.let { "${it.toInt()} Hz" } ?: "waiting"}",color=Color.Gray,style=MaterialTheme.typography.bodySmall)
     Text("Use ADD DETECTED POINT in the main screen",color=Color.Gray,style=MaterialTheme.typography.bodySmall)
    } else {
     Text("${current.target}  ·  ${current.hz} Hz",color=Color.White,style=MaterialTheme.typography.titleSmall)
     Text("Suggested cut ${current.cutDb} dB  ·  ${current.status}",color=Color(0xFFE3BA70),style=MaterialTheme.typography.bodySmall)
     Row(horizontalArrangement=Arrangement.spacedBy(3.dp)) {
      points.take(5).forEachIndexed { i,p ->
       TextButton(onClick={onSelect(p.id)},contentPadding=PaddingValues(2.dp)) {
        Text("${i+1}",color=if(p.id==current.id) Color(0xFFE3BA70) else Color.LightGray)
       }
      }
     }
     val assist=mode==AiOperatingMode.ASSIST_CONFIRM
     Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
      Button(onClick={onReview(current.id)},enabled=assist && current.status==SuggestionStatus.PENDING,contentPadding=PaddingValues(horizontal=7.dp)) { Text("Review") }
      Button(onClick={onConfirm(current.id)},enabled=assist && current.status==SuggestionStatus.REVIEWED,contentPadding=PaddingValues(horizontal=7.dp)) { Text("Confirm") }
     }
     Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
      OutlinedButton(onClick={onUndo(current.id)},enabled=assist && current.status!=SuggestionStatus.PENDING,contentPadding=PaddingValues(horizontal=7.dp)) { Text("Undo") }
      OutlinedButton(onClick={onIgnore(current.id)},enabled=assist && (current.status==SuggestionStatus.PENDING || current.status==SuggestionStatus.REVIEWED),contentPadding=PaddingValues(horizontal=7.dp)) { Text("Ignore") }
      OutlinedButton(onClick=onNext,contentPadding=PaddingValues(horizontal=7.dp)) { Text("Next") }
     }
    }
    Text("PREVIEW ONLY · MIXER WRITE LOCKED",color=Color(0xFF9FA9B6),style=MaterialTheme.typography.labelSmall)
   }
  }
 }
 }
}

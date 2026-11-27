package com.kai.soundgood.aigeq

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioManager
import android.content.Context
import android.media.MediaRecorder
import android.os.Bundle
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import androidx.compose.ui.unit.sp
import android.content.pm.ActivityInfo
import androidx.compose.ui.platform.LocalConfiguration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val gold=Color(0xFFD9AA59)
private val muted=Color(0xFF9CA3AF)
@Composable internal fun EqSafetyPanel(target:EqTarget,connected:Boolean) {
 val scope=rememberCoroutineScope()
  var safetyBaseline by remember { mutableStateOf<M32PeqSnapshot?>(null) }
  var safetyStatus by remember { mutableStateOf("NO BASELINE · EQ LOCKED") }
  var safetyBusy by remember { mutableStateOf(false) }
       Text("OSC EQ SAFETY · READ ONLY",color=gold,fontSize=10.sp)
       Text(safetyStatus,color=muted,fontSize=10.sp)
       Text("Baseline: "+(safetyBaseline?.target?.label ?: "NONE")+" · all bands reserved",color=muted,fontSize=10.sp)
       Row(verticalAlignment=Alignment.CenterVertically) {
        TextButton(enabled=connected&&!safetyBusy,onClick={
         val requested=target
         safetyBusy=true;safetyStatus="READING BASELINE · EQ LOCKED";safetyBaseline=null
         scope.launch {
          try {
           val snap=withContext(Dispatchers.IO) {
            val client=OscClient()
            try {client.connect();M32LiveStateReader(client).inspectPeq(requested)} finally {client.close()}
           }
           if(snap.complete) {safetyBaseline=snap;safetyStatus="BASELINE CAPTURED · EQ LOCKED"}
           else safetyStatus="INCOMPLETE BASELINE · EQ LOCKED"
          } catch(e:Exception){safetyStatus="BASELINE FAILED · EQ LOCKED"}
          finally{safetyBusy=false}
         }
        }) {Text("BACKUP PEQ",fontSize=10.sp)}
        TextButton(enabled=connected&&!safetyBusy&&safetyBaseline?.target==target,onClick={
         val baseline=safetyBaseline
         val requested=target
         safetyBusy=true
         scope.launch {
          try {
           val snap=withContext(Dispatchers.IO) {
            val client=OscClient()
            try {client.connect();M32LiveStateReader(client).inspectPeq(requested)} finally {client.close()}
           }
           safetyStatus=when {
            baseline==null || !snap.complete -> "READBACK INCOMPLETE · EQ LOCKED"
            snap.values!=baseline.values -> "EQ CHANGED EXTERNALLY · EQ LOCKED"
            else -> "BASELINE MATCH · EQ LOCKED"
           }
          } catch(e:Exception){safetyStatus="READBACK FAILED · EQ LOCKED"}
          finally{safetyBusy=false}
         }
        }) {Text("VERIFY PEQ",fontSize=10.sp)}
        TextButton(enabled=!safetyBusy,onClick={safetyBaseline=null;safetyStatus="NO BASELINE · EQ LOCKED"}) {Text("CLEAR",fontSize=10.sp)}
       }
       Text("RAM backup only. No restore or OSC EQ write.",color=muted,fontSize=9.sp)

}

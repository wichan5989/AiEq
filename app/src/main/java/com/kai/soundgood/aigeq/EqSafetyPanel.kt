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
@Composable internal fun EqSafetyPanel(target:EqTarget,connected:Boolean,peakHz:Int?=null,spectrumHealthy:Boolean=false) {
 val scope=rememberCoroutineScope()
 val context=androidx.compose.ui.platform.LocalContext.current
 val prefs=remember(context){context.getSharedPreferences("peq_safety_v1",Context.MODE_PRIVATE)}
 val key="baseline_"+target.kind.name+"_"+target.number
 fun encode(snapshot:M32PeqSnapshot):String {
  val values=org.json.JSONObject()
  snapshot.values.toSortedMap().forEach { (path,args) ->
   val array=org.json.JSONArray()
   args.forEach { arg ->
    val item=org.json.JSONObject()
    when(arg) {
     is Float -> {item.put("type","f");item.put("value",arg.toRawBits())}
     is Int -> {item.put("type","i");item.put("value",arg)}
     is String -> {item.put("type","s");item.put("value",arg)}
     is Boolean -> {item.put("type","b");item.put("value",arg)}
     else -> error("Unsupported backup value")
    }
    array.put(item)
   }
   values.put(path,array)
  }
  return values.toString()
 }
 var stored by remember(key){mutableStateOf(prefs.getString(key,null))}
 var reserved by remember(key){mutableStateOf(emptySet<Int>())}
 val currentKey by rememberUpdatedState(key)
 var verifiedBands by remember(key,connected,stored,reserved){mutableStateOf<List<DecodedPeqBand>>(emptyList())}
 var restorePreview by remember(key,connected,stored,reserved){mutableStateOf<List<String>>(emptyList())}
 val currentPlan by rememberUpdatedState(reserved)
 val currentConnected by rememberUpdatedState(connected)
 fun compareBackup(baseline:String,live:String,plan:Set<Int>):List<String> {
  val saved=org.json.JSONObject(baseline)
  val actual=org.json.JSONObject(live)
  require(saved.keys().asSequence().toSet()==actual.keys().asSequence().toSet())
  fun display(array:org.json.JSONArray):String =
   (0 until array.length()).joinToString(",") { i ->
    val item=array.getJSONObject(i)
    if(item.getString("type")=="f") Float.fromBits(item.getInt("value")).toString()
    else item.get("value").toString()
   }
  return saved.keys().asSequence().sorted().mapNotNull { path ->
   val old=saved.getJSONArray(path)
   val now=actual.getJSONArray(path)
   if(old.toString()==now.toString()) null else {
    val band=path.substringBeforeLast("/").substringAfterLast("/").toIntOrNull()
    val label=if(band!=null && band in plan) "PLAN" else "KEEP"
    "$label · $path: "+display(now)+" → "+display(old)
   }
  }.toList()
 }

  var safetyStatus by remember(key,connected,stored,reserved) { mutableStateOf("NO BASELINE · EQ LOCKED") }
  var safetyBusy by remember { mutableStateOf(false) }
       Text("OSC EQ SAFETY · READ ONLY",color=gold,fontSize=10.sp)
       Text(safetyStatus,color=muted,fontSize=10.sp)
       Text("Baseline: "+(if(stored!=null) target.label+" · SAVED ON DEVICE" else "NONE"),color=muted,fontSize=10.sp)
       Text("PREVIEW BAND ALLOCATION · no write permission",color=gold,fontSize=10.sp)
       Row(verticalAlignment=Alignment.CenterVertically) {
        for(band in 1..target.peqBands) {
         TextButton(enabled=stored!=null&&!safetyBusy,onClick={
          reserved=if(band in reserved) reserved-band else reserved+band
         }) {Text("B$band "+if(band in reserved) "PLAN" else "KEEP",fontSize=10.sp)}
        }
       }
       Row(verticalAlignment=Alignment.CenterVertically) {
        TextButton(enabled=connected&&!safetyBusy,onClick={
         val requested=target
         safetyBusy=true;safetyStatus="READING BASELINE · EQ LOCKED"
         scope.launch {
          try {
           val snap=withContext(Dispatchers.IO) {
            val client=OscClient()
            try {client.connect();M32LiveStateReader(client).inspectPeq(requested)} finally {client.close()}
           }
           if(snap.complete) {val encoded=encode(snap);val saved=withContext(Dispatchers.IO){prefs.edit().putString(key,encoded).commit()};if(currentKey==key){if(saved){stored=encoded;reserved=emptySet();safetyStatus="BASELINE SAVED · EQ LOCKED"}else safetyStatus="SAVE FAILED · EQ LOCKED"}}
           else safetyStatus="INCOMPLETE BASELINE · EQ LOCKED"
          } catch(e:Exception){safetyStatus="BASELINE FAILED · EQ LOCKED"}
          finally{safetyBusy=false}
         }
        }) {Text("BACKUP PEQ",fontSize=10.sp)}
        TextButton(enabled=connected&&!safetyBusy&&stored!=null,onClick={
         val baseline=stored
         val plan=reserved
         restorePreview=emptyList();verifiedBands=emptyList()
         val requested=target
         safetyBusy=true
         scope.launch {
          try {
           val snap=withContext(Dispatchers.IO) {
            val client=OscClient()
            try {client.connect();M32LiveStateReader(client).inspectPeq(requested)} finally {client.close()}
           }
           if(currentKey==key && currentConnected && currentPlan==plan) {
           if(baseline!=null && snap.complete) {
            restorePreview=compareBackup(baseline,encode(snap),plan)
            if(restorePreview.isEmpty()) verifiedBands=LivePeqDecoder.decode(snap).takeIf {it.size==requested.peqBands} ?: emptyList()
           }
           safetyStatus=when {
            baseline==null || !snap.complete -> "READBACK INCOMPLETE · EQ LOCKED"
            encode(snap)!=baseline -> "EQ CHANGED EXTERNALLY · EQ LOCKED"
            else -> "BASELINE MATCH · EQ LOCKED"
           }
           }
          } catch(e:Exception){safetyStatus="READBACK FAILED · EQ LOCKED"}
          finally{safetyBusy=false}
         }
        }) {Text("VERIFY / RESTORE PREVIEW",fontSize=10.sp)}
        TextButton(enabled=!safetyBusy,onClick={prefs.edit().remove(key).apply();stored=null;reserved=emptySet();safetyStatus="NO BASELINE · EQ LOCKED"}) {Text("CLEAR",fontSize=10.sp)}
       }
       if(restorePreview.isNotEmpty()) {
        Text("RESTORE PREVIEW · ${restorePreview.size} differences · NO WRITE",color=gold,fontSize=10.sp)
        Column(Modifier.heightIn(max=100.dp).verticalScroll(rememberScrollState())) {
         restorePreview.forEach { Text(it,color=muted,fontSize=9.sp) }
        }
       }
       ManualDynamicPreview(target,reserved,connected && !safetyBusy && safetyStatus=="BASELINE MATCH · EQ LOCKED",peakHz,spectrumHealthy,verifiedBands)
       Text("Local persistent backup. PLAN is preview only. No restore or OSC EQ write.",color=muted,fontSize=9.sp)

}

@Composable private fun ManualDynamicPreview(target:EqTarget,plan:Set<Int>,ready:Boolean,peakHz:Int?,spectrumHealthy:Boolean,verifiedBands:List<DecodedPeqBand>) {
 var band by remember(target,plan,ready,verifiedBands){mutableIntStateOf(verifiedBands.filter {it.number in plan && it.type==2}.minOfOrNull {it.number} ?: 0)}
 var trigger by remember(target,plan,ready){mutableStateOf(false)}
 var maxCut by remember {mutableFloatStateOf(1f)}
 var attack by remember {mutableIntStateOf(150)}
 var hold by remember {mutableIntStateOf(800)}
 var release by remember {mutableIntStateOf(1500)}
 var cuts by remember(target,plan,ready){mutableStateOf<Map<Int,Float>>(emptyMap())}
 val eligible=verifiedBands.filter {it.number in plan && it.type==2 && it.hz.isFinite() && it.hz in 20.0..20000.0}
 val frequencies=eligible.associate {it.number to it.hz.toFloat()}
 val eligiblePlan=frequencies.keys
 var auto by remember(target,plan,ready,spectrumHealthy){mutableStateOf(false)}
 val matched=if(spectrumHealthy && peakHz!=null && peakHz>0) frequencies.filterKeys {it in plan}.filterValues {kotlin.math.abs(kotlin.math.ln(it.toDouble()/peakHz))<=kotlin.math.ln(2.0)/6}.minByOrNull {kotlin.math.abs(kotlin.math.ln(it.value.toDouble()/peakHz))}?.key else null
 val activeBand by rememberUpdatedState(if(auto) matched else if(trigger) band else null)
 LaunchedEffect(target,plan,ready,band,maxCut,attack,hold,release,spectrumHealthy,auto,verifiedBands) {
  cuts=emptyMap()
  if(!ready || band !in eligiblePlan) return@LaunchedEffect
  val envelopes=eligiblePlan.associateWith {ManualDynamicEnvelope()}
  var previous=android.os.SystemClock.elapsedRealtime()
  while(isActive) {
   val now=android.os.SystemClock.elapsedRealtime()
   val raw=envelopes.mapValues {(b,e)->e.step(now,now-previous,activeBand==b,maxCut,attack,hold,release)}
   val total=raw.values.sumOf {kotlin.math.abs(it).toDouble()}
   val scale=if(total>6.0) (6.0/total).toFloat() else 1f
   cuts=raw.mapValues {it.value*scale}
   previous=now
   delay(40)
  }
 }
 Text("DYNAMIC · LOCAL TEST ONLY",color=gold,fontSize=10.sp)
 Row(verticalAlignment=Alignment.CenterVertically) {
  TextButton(enabled=ready&&band in eligiblePlan&&spectrumHealthy&&frequencies.isNotEmpty(),onClick={trigger=false;auto=!auto}){Text(if(auto) "PEAK TRIGGER ON" else "PEAK TRIGGER OFF",fontSize=10.sp)}
  Text(if(spectrumHealthy) (peakHz?.let {"Candidate $it Hz"} ?: "Listening") else "OSC NOT READY",color=muted,fontSize=9.sp)
 }
 Text(if(ready && band in eligiblePlan) "Matched: "+(matched?.let {"B$it"} ?: "NONE")+" · total cut "+ "%.2f".format(cuts.values.sum())+" dB" else "Select PLAN and verify baseline first",color=muted,fontSize=10.sp)
 if(ready && band in eligiblePlan) {
  Text("OSC decoded F/G/Q · provisional mapping · preview only",color=muted,fontSize=9.sp)
  eligiblePlan.sorted().forEach { b ->
   Row(verticalAlignment=Alignment.CenterVertically) {
    val info=eligible.first {it.number==b}
    Text("B$b "+ "%.0f Hz · %+.1f dB · Q %.2f".format(info.hz,info.gainDb,info.q),color=muted,fontSize=9.sp)
    LinearProgressIndicator(progress={(-(cuts[b] ?: 0f)/3f).coerceIn(0f,1f)},modifier=Modifier.weight(1f).padding(horizontal=6.dp))
    Text("%.2f".format(cuts[b] ?: 0f)+" dB",fontSize=9.sp)
   }
  }
 }
 Row(verticalAlignment=Alignment.CenterVertically) {
  TextButton(enabled=ready&&eligiblePlan.isNotEmpty(),onClick={trigger=false;band=eligiblePlan.sorted().let {it[(it.indexOf(band)+1)%it.size]}}){Text("B$band",fontSize=10.sp)}
  TextButton(enabled=ready&&band in eligiblePlan&&!auto,onClick={trigger=!trigger}){Text(if(trigger) "RELEASE" else "TRIGGER",fontSize=10.sp)}
  TextButton(onClick={maxCut=if(maxCut>=3f) 1f else maxCut+1f}){Text("MAX $maxCut dB",fontSize=10.sp)}
 }
 Row(verticalAlignment=Alignment.CenterVertically) {
  TextButton(onClick={attack=if(attack==150) 50 else 150}){Text("ATT $attack",fontSize=10.sp)}
  TextButton(onClick={hold=if(hold==800) 300 else 800}){Text("HOLD $hold",fontSize=10.sp)}
  TextButton(onClick={release=if(release==1500) 500 else 1500}){Text("REL $release",fontSize=10.sp)}
 }
}

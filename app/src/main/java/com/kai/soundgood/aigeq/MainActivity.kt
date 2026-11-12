package com.kai.soundgood.aigeq

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioManager
import android.content.Context
import android.media.MediaRecorder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val bg=Color(0xFF101216)
private val panel=Color(0xFF20242A)
private val gold=Color(0xFFD9AA59)
private val muted=Color(0xFF9CA3AF)
class MainActivity:ComponentActivity() {
 override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); setContent { Screen() } }
 @Composable private fun Screen() {
  val scope= rememberCoroutineScope()
  var connected by remember { mutableStateOf(false) }
  var detecting by remember { mutableStateOf(false) }
  var candidate by remember { mutableStateOf<Candidate?>(null) }
  var approved by remember { mutableStateOf(false) }
  var aiMode by remember { mutableStateOf(AiOperatingMode.MONITOR_ONLY) }
  var pendingPoint by remember { mutableStateOf<ProposedPoint?>(null) }
  var previewConfirmed by remember { mutableStateOf(false) }
  val suggestionQueue = remember { FeedbackSuggestionQueue() }
  var queueRevision by remember { mutableIntStateOf(0) }
  var advisorExpanded by remember { mutableStateOf(false) }
  var advisorVisible by remember { mutableStateOf(true) }
  fun refreshQueue() { queueRevision++ }
  fun resetApproval() { approved=false; pendingPoint=null; previewConfirmed=false; suggestionQueue.clear(); refreshQueue() }
  var aiPreference by remember { mutableStateOf<FixTool?>(null) }
  val aiEngine = remember { AiFeedbackEngine() }
  val mixerAdapter = remember { M32Adapter() }
  var targetKind by remember { mutableStateOf(TargetKind.BUS) }
  var targetNumber by remember { mutableIntStateOf(1) }
  var eqChoice by remember { mutableStateOf(EqKind.OFF) }
  var rtaSourceMode by remember { mutableStateOf(RtaSourceMode.FOLLOW) }
  var rtaSourceKind by remember { mutableStateOf(TargetKind.CHANNEL) }
  var rtaSourceNumber by remember { mutableIntStateOf(1) }
  var measurementMicChannel by remember { mutableIntStateOf(32) }
  var rtaLocked by remember { mutableStateOf(false) }
  var frozenRtaSource by remember { mutableStateOf<EqTarget?>(null) }
  var followLayers by remember { mutableStateOf(true) }
  var layer by remember { mutableIntStateOf(1) }
  val target = EqTarget(targetKind, targetNumber)
  val unlockedRtaSource = when(rtaSourceMode) {
   RtaSourceMode.FOLLOW -> target
   RtaSourceMode.MANUAL -> EqTarget(rtaSourceKind,rtaSourceNumber)
   RtaSourceMode.MEASUREMENT_MIC -> EqTarget(TargetKind.CHANNEL,measurementMicChannel)
  }
  val activeRtaSource = if(rtaLocked) frozenRtaSource ?: unlockedRtaSource else unlockedRtaSource
  val rtaMax = when(rtaSourceKind) { TargetKind.CHANNEL->32; TargetKind.BUS->16; TargetKind.MAIN_LR->1; TargetKind.MATRIX->6 }

  // Until live console state is read, capabilities must remain unknown.
  val caps = EqCapabilities()
  val maxTarget = when(targetKind) { TargetKind.CHANNEL -> 32; TargetKind.BUS -> 16; TargetKind.MAIN_LR -> 1; TargetKind.MATRIX -> 6 }

  var message by remember { mutableStateOf("Offline · GEQ write locked") }
  var peqSnapshot by remember { mutableStateOf<M32PeqSnapshot?>(null) }
  val client= remember { OscClient() }
  val detector= remember { FeedbackDetector() }
  val rtaAnalyzer= remember { RtaAnalyzer() }
  var rtaLevels by remember { mutableStateOf(List(31) {-120f}) }
  var captureFrames by remember { mutableIntStateOf(0) }
  val sessionGuard=remember { LiveAudioSessionGuard() }
  var liveSession by remember { mutableStateOf(sessionGuard.status(android.os.SystemClock.elapsedRealtime())) }
  var captureHealth by remember { mutableStateOf(AudioCaptureDiagnostics().snapshot()) }
  var inputWarning by remember { mutableStateOf("") }
  var recoveryStatus by remember { mutableStateOf(RecoveryStatus(CapturePhase.IDLE,"Capture stopped",true)) }
  var devices by remember { mutableStateOf(AudioInputManager.available(this@MainActivity)) }
  var chosenDeviceId by remember { mutableStateOf<Int?>(null) }
  var actualDeviceId by remember { mutableStateOf<Int?>(null) }
  var routingVerified by remember { mutableStateOf(false) }
  val selectedDevice = devices.firstOrNull { it.id == chosenDeviceId }

  var rtaHistory by remember { mutableStateOf<List<List<Float>>>(emptyList()) }
  var rtaMode by remember { mutableStateOf(RtaMode.BAR) }
  var rtaFloor by remember { mutableFloatStateOf(-90f) }
  var rtaStyle by remember { mutableStateOf(RtaStyle.PIXEL_LED) }
  val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (!granted) message="Microphone permission denied" else detecting=true }
  DisposableEffect(Unit) { onDispose { client.close() } }
  LaunchedEffect(detecting) {
   if (!detecting) return@LaunchedEffect
   withContext(Dispatchers.IO) {
    val min=AudioRecord.getMinBufferSize(48000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
    if (min<=0) { message="Audio input unavailable"; detecting=false; return@withContext }
    val recorder=AudioRecord(MediaRecorder.AudioSource.UNPROCESSED,48000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(min,8192))
    if (recorder.state!=AudioRecord.STATE_INITIALIZED) { recorder.release(); message="AudioRecord initialization failed"; detecting=false; return@withContext }
    sessionGuard.begin()
    liveSession=sessionGuard.status(android.os.SystemClock.elapsedRealtime())
    val healthMonitor=AudioCaptureDiagnostics()
    val assembler=PcmFrameAssembler(4096)
    val recovery=CaptureRecoveryPolicy()
    recovery.start(android.os.SystemClock.elapsedRealtime(),chosenDeviceId)
    try {
     if(chosenDeviceId != null && !AudioInputManager.route(recorder,this@MainActivity,chosenDeviceId!!)) {
      message="Selected input missing; capture cancelled"; detecting=false; return@withContext
     }
     recorder.startRecording()
     if(recorder.recordingState!=AudioRecord.RECORDSTATE_RECORDING) {
      inputWarning="AudioRecord failed to start"; detecting=false; return@withContext
     }
     if(recorder.sampleRate!=48000) {
      inputWarning="Unexpected sample rate ${recorder.sampleRate} Hz (expected 48000); analysis stopped"
      detecting=false; return@withContext
     }
     actualDeviceId=AudioInputManager.routedId(recorder)
     routingVerified=chosenDeviceId == null || actualDeviceId==chosenDeviceId
     sessionGuard.routeVerified(routingVerified)
     if(!routingVerified) { message="Audio routed to unexpected device; analysis stopped"; detecting=false; return@withContext }
     val data=ShortArray(4096)
     while (detecting) {
      val n=recorder.read(data,0,data.size,AudioRecord.READ_NON_BLOCKING)
      val now=System.currentTimeMillis()
      if(n>0) {
       recovery.read(android.os.SystemClock.elapsedRealtime(),n)
       captureHealth=healthMonitor.accept(data,n,data.size,now)
      } else if(n<0) captureHealth=healthMonitor.accept(data,n,data.size,now)
      val selectedPresent=chosenDeviceId==null || AudioInputManager.available(this@MainActivity).any { it.id==chosenDeviceId }
      recoveryStatus=recovery.check(android.os.SystemClock.elapsedRealtime(),AudioInputManager.routedId(recorder),selectedPresent)
      if(recoveryStatus.phase==CapturePhase.LOST || recoveryStatus.phase==CapturePhase.WAITING_FOR_DEVICE) {
       inputWarning=recoveryStatus.detail; detecting=false; break
      }
      if(n<0) { inputWarning="AudioRecord read error: $n"; detecting=false;break }
      if(chosenDeviceId!=null && AudioInputManager.routedId(recorder)!=chosenDeviceId) {
       inputWarning="USB/input route changed; capture stopped";detecting=false;break
      }
      if(n>0) {
       inputWarning=if(n<data.size) "Partial read ($n samples); assembling full FFT window" else ""
       assembler.append(data,n) { frame ->
        val levels=rtaAnalyzer.measure(frame)
        rtaLevels=levels; captureFrames++
        sessionGuard.completeFrame(android.os.SystemClock.elapsedRealtime())
        liveSession=sessionGuard.status(android.os.SystemClock.elapsedRealtime())
        rtaHistory=(rtaHistory+listOf(levels)).takeLast(90)
        val next=detector.analyze(frame,System.currentTimeMillis())
        if(next!=null && liveSession.canUseForAdvice) { candidate=next }
       }
      } else if(n==0) delay(10)
     }
    } catch (e:Exception) { message="Audio error: ${e.message}"; detecting=false }
    finally { recovery.stop(); sessionGuard.end(); liveSession=sessionGuard.status(android.os.SystemClock.elapsedRealtime()); candidate=null; resetApproval(); try { recorder.stop() } catch (_:Exception) {}; recorder.release(); actualDeviceId=null; routingVerified=false }
   }
  }
  MaterialTheme(colorScheme=darkColorScheme(primary=gold,background=bg,surface=panel,onBackground=Color.White,onSurface=Color.White)) {
   Box(Modifier.fillMaxSize().background(bg)) {
   Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) {
    Text("KAI SOUNDGOOD",fontWeight=FontWeight.Black,style=MaterialTheme.typography.headlineMedium,color=Color.White)
    Text("AI GEQ   /   MIDAS M32 SERIES",color=gold,fontWeight=FontWeight.Bold)
    Text("ASSIST MODE  •  MEDIUM  •  MAX CUT −3 dB",color=muted)
    HorizontalDivider(color=Color.DarkGray)
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
     Button(onClick={ scope.launch(Dispatchers.IO) { try { client.connect(); client.sendQuery("/xinfo"); val response=client.receive(); connected=response!=null; message=if(connected) "OSC response: ${OscClient.addressOf(response!!)}" else "No OSC reply (check network)" } catch(e:Exception) { connected=false; message=e.message?:"OSC error" } } }) { Text(if(connected) "RECONNECT" else "CONNECT M32") }
     Text(if(connected) "OSC REPLIED" else "DISCONNECTED",color=if(connected) Color(0xFF73D7A0) else gold)
    }
    Text("192.168.0.100 : 10023 · READ ONLY",color=muted)
    Text(message,color=muted)
    HorizontalDivider(color=Color.DarkGray)
    Text("LIVE READINESS · v3.6",color=gold,fontWeight=FontWeight.Bold)
    Text("ANDROID AUDIO INPUT",color=gold,fontWeight=FontWeight.Bold)
    Text("Capture: ${recoveryStatus.phase} · ${recoveryStatus.detail}",color=muted)
    OutlinedButton(onClick={ if(!detecting) devices=AudioInputManager.available(this@MainActivity) },enabled=!detecting) { Text("REFRESH AUDIO INPUTS") }
    Text("Choose an input before starting capture. USB audio requires an OTG-compatible interface.",color=muted)
    Row(horizontalArrangement=Arrangement.spacedBy(5.dp),modifier=Modifier.horizontalScroll(rememberScrollState())) {
     FilterChip(selected=chosenDeviceId==null,onClick={if(!detecting) chosenDeviceId=null},enabled=!detecting,label={Text("SYSTEM DEFAULT")})
     devices.forEach { d -> FilterChip(selected=chosenDeviceId==d.id,onClick={if(!detecting) chosenDeviceId=d.id},enabled=!detecting,label={Text("${if(d.usb) "USB " else ""}${d.name} (#${d.id})")}) }
    }
    Text("Requested: ${selectedDevice?.name ?: "System default"} · Actual input ID: ${actualDeviceId ?: "none"} · Route: ${if(routingVerified) "verified" else "not verified"}",color=muted)
    Text("CH/Bus RTA selection does not automatically route mixer audio into Android. Use a physical audio feed from the desired mixer output.",color=muted,style=MaterialTheme.typography.bodySmall)
    val readiness = LiveReadiness.evaluate(ReadinessInput(
     oscConnected=connected,
     audioRunning=detecting && captureFrames>0,
     feed=AudioFeed.ANDROID_MIC,
     mixerSourceSelected=true,
     routingVerified=false,
     eqSnapshotComplete=peqSnapshot?.complete == true
    ))
    Text("AI input freshness: ${if(liveSession.canUseForAdvice) "FRESH" else "NOT READY"} · complete FFT frames ${liveSession.completeFrames} · last frame age ${liveSession.lastFrameAgeMs ?: "n/a"} ms",color=gold)
    Text("Capture health: ${captureHealth.blocks} blocks · ${captureHealth.shortReads} short reads · ${captureHealth.errors} errors",color=muted)
    Text("Level: RMS ${"%.1f".format(captureHealth.rmsDbfs)} dBFS · Peak ${"%.1f".format(captureHealth.peakDbfs)} dBFS · Silent ${captureHealth.silentBlocks} · Clipped ${captureHealth.clippedBlocks}",color=muted)
    if(inputWarning.isNotEmpty()) Text(inputWarning,color=gold)
    Text("Audio frames: $captureFrames · Capture: ${if(detecting) "ON" else "OFF"} · OSC: ${if(connected) "REPLIED" else "OFFLINE"}",color=Color.White)
    Text("Analysis: ${if(readiness.canAnalyze) "ANDROID MIC ONLY" else "NOT READY"} · EQ Write: LOCKED",color=gold)
    readiness.warnings.forEach { Text("• $it",color=muted,style=MaterialTheme.typography.bodySmall) }
    HorizontalDivider(color=Color.DarkGray)
    Text("RTA SOURCE · INDEPENDENT ROUTING",color=gold,fontWeight=FontWeight.Bold)
    Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
     RtaSourceMode.entries.forEach { m ->
      FilterChip(selected=rtaSourceMode==m,onClick={rtaSourceMode=m},label={Text(when(m) {RtaSourceMode.FOLLOW->"FOLLOW";RtaSourceMode.MANUAL->"MANUAL";RtaSourceMode.MEASUREMENT_MIC->"RTA MIC"})})
     }
    }
    if(rtaSourceMode==RtaSourceMode.MANUAL) {
     Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
      TargetKind.entries.forEach { k ->
       FilterChip(selected=rtaSourceKind==k,onClick={rtaSourceKind=k;rtaSourceNumber=1},label={Text(k.name.take(3))})
      }
     }
     Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
      OutlinedButton(onClick={rtaSourceNumber=(rtaSourceNumber-1).coerceAtLeast(1)},enabled=rtaSourceNumber>1) {Text("−")}
      Text(EqTarget(rtaSourceKind,rtaSourceNumber).label,color=Color.White)
      OutlinedButton(onClick={rtaSourceNumber=(rtaSourceNumber+1).coerceAtMost(rtaMax)},enabled=rtaSourceNumber<rtaMax) {Text("+")}
     }
    }
    if(rtaSourceMode==RtaSourceMode.MEASUREMENT_MIC) {
     Text("Measurement mic connected to mixer input",color=muted)
     Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
      OutlinedButton(onClick={measurementMicChannel=(measurementMicChannel-1).coerceAtLeast(1)},enabled=measurementMicChannel>1) {Text("−")}
      Text("CH %02d".format(measurementMicChannel),color=Color.White)
      OutlinedButton(onClick={measurementMicChannel=(measurementMicChannel+1).coerceAtMost(32)},enabled=measurementMicChannel<32) {Text("+")}
     }
    }
    Row(verticalAlignment=Alignment.CenterVertically) {
     Switch(checked=rtaLocked,onCheckedChange={value-> if(value) frozenRtaSource=unlockedRtaSource else frozenRtaSource=null; rtaLocked=value})
     Spacer(Modifier.width(10.dp))
     Text(if(rtaLocked) "RTA LOCKED" else "RTA FOLLOW / UNLOCKED",color=Color.White)
    }
    Text("RTA SOURCE: ${activeRtaSource.label}  |  EQ TARGET: ${target.label}",color=gold)
    Text("SOURCE ROUTING IS UI-ONLY: M32 OSC does not supply verified PCM spectrum. Live graph below uses the selected Android input (microphone or USB), NOT selected mixer channel/bus. For a mic connected to M32, route its signal to a supported USB audio input on Android or implement a verified RTA transport.",color=muted,style=MaterialTheme.typography.bodySmall)
    RtaControls(rtaMode,{rtaMode=it},rtaFloor,{rtaFloor=it},rtaStyle,{rtaStyle=it})
    RtaDisplay(rtaLevels,rtaHistory,rtaMode,rtaFloor,rtaStyle,Modifier.fillMaxWidth().height(210.dp))
    Text("EQ ROUTING · TARGET",color=gold,fontWeight=FontWeight.Bold)
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
     TargetKind.entries.forEach { kind ->
      OutlinedButton(onClick={ targetKind=kind; targetNumber=1; eqChoice=EqKind.OFF; resetApproval() },contentPadding=PaddingValues(horizontal=8.dp, vertical=2.dp)) {
       Text(when(kind) { TargetKind.CHANNEL->"CH"; TargetKind.BUS->"BUS"; TargetKind.MAIN_LR->"LR"; TargetKind.MATRIX->"MTX" },color=if(targetKind==kind) gold else muted)
      }
     }
    }
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
     OutlinedButton(onClick={ if(targetNumber>1) { targetNumber--; resetApproval() } },enabled=targetNumber>1) { Text("−") }
     Text(target.label,color=Color.White,fontWeight=FontWeight.Bold)
     OutlinedButton(onClick={ if(targetNumber<maxTarget) { targetNumber++; resetApproval() } },enabled=targetNumber<maxTarget) { Text("+") }
    }
    Text("PEQ: ${target.peqBands} bands · status UNKNOWN  |  GEQ: FX routing UNKNOWN",color=muted)
    Button(enabled=connected,onClick={scope.launch(Dispatchers.IO) {
     try {
      val snapshot=M32LiveStateReader(client).inspectPeq(target)
      peqSnapshot=snapshot
      message=if(snapshot.complete) "PEQ snapshot received (read-only; routing NOT verified)" else "Partial PEQ snapshot: ${snapshot.missing.size} replies missing"
     } catch(e:Exception) { message="PEQ inspection failed: ${e.message}" }
    }}) { Text("READ PEQ STATE · NO WRITES") }
    peqSnapshot?.let { snapshot ->
     Text("Last inspected: ${snapshot.target.label} · ${snapshot.values.size} responses · ${snapshot.missing.size} missing · ${snapshot.unexpected} unmatched",color=muted)
     Text(if(snapshot.complete) "PEQ query responses complete; capability remains unverified" else "Incomplete snapshot — no control changes allowed",color=gold)
    }

    Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
     EqKind.entries.forEach { kind ->
      FilterChip(selected=eqChoice==kind,onClick={eqChoice=kind; resetApproval()},label={Text(kind.name)},enabled=kind==EqKind.OFF || allowedEq(target,caps,kind))
     }
    }
    Text("EQ changes disabled until live PEQ/GEQ capability and FX routing are verified.",color=muted,style=MaterialTheme.typography.bodySmall)
    HorizontalDivider(color=Color.DarkGray)
    Text("FOLLOW LAYERS",color=gold,fontWeight=FontWeight.Bold)
    Row(verticalAlignment=Alignment.CenterVertically) {
     Switch(checked=followLayers,onCheckedChange={followLayers=it})
     Spacer(Modifier.width(10.dp))
     Text(if(followLayers) "Follow app mixer layer" else "Lock selected target",color=Color.White)
    }
    Text("Layer $layer · Local selection preview (not synced to M32)",color=muted)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     (1..4).forEach { l ->
      OutlinedButton(onClick={layer=l; if(followLayers) { targetKind=TargetKind.CHANNEL; targetNumber=(l-1)*8+1; eqChoice=EqKind.OFF; resetApproval() }},contentPadding=PaddingValues(horizontal=10.dp)) { Text("L$l") }
     }
    }
    HorizontalDivider(color=Color.DarkGray)
    Text("31-BAND GEQ · PREVIEW",color=gold,fontWeight=FontWeight.Bold)
    LazyRow(horizontalArrangement=Arrangement.spacedBy(5.dp),modifier=Modifier.fillMaxWidth()) {
     itemsIndexed(detector.centers.toList()) { index,hz ->
      val selected=candidate?.hz==hz
      Column(Modifier.width(33.dp).background(panel,RoundedCornerShape(5.dp)).padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
       Text(if(selected) "!" else "0",color=if(selected) gold else muted)
       Box(Modifier.height(95.dp).width(3.dp).background(Color.DarkGray))
       Box(Modifier.width(25.dp).height(11.dp).background(if(selected) gold else Color.LightGray,RoundedCornerShape(3.dp)))
       Text(if(hz>=1000) "${hz/1000}k" else hz.toInt().toString(),color=muted,style=MaterialTheme.typography.labelSmall)
      }
     }
    }
    Text("LIVE AUDIO: Android input device (microphone or USB) · mixer CH/Bus routing requires external feed",color=muted,style=MaterialTheme.typography.bodySmall)
    Button(onClick={ if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) detecting=!detecting else permission.launch(Manifest.permission.RECORD_AUDIO) }) { Text(if(detecting) "STOP DETECTOR" else "START DETECTOR") }
    Text("AI OPERATING MODE · v3.6",color=gold,fontWeight=FontWeight.Bold)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     FilterChip(selected=aiMode==AiOperatingMode.MONITOR_ONLY,onClick={aiMode=AiOperatingMode.MONITOR_ONLY;resetApproval()},label={Text("MONITOR ONLY")})
     FilterChip(selected=aiMode==AiOperatingMode.ASSIST_CONFIRM,onClick={aiMode=AiOperatingMode.ASSIST_CONFIRM;resetApproval()},label={Text("ASSIST + CONFIRM")})
    }
    Text(if(aiMode==AiOperatingMode.MONITOR_ONLY) "READ-ONLY BYPASS: RTA and suggestions remain active; all approvals disabled" else "Per-point review: select a detected frequency, then confirm preview. Mixer writes remain locked",color=muted)
    Text("AI FEEDBACK ASSIST · v3.6",color=gold,fontWeight=FontWeight.Bold)
    Text("Policy engine (not a trained AI model) · PEQ / GEQ / Gate guidance",color=muted)
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
     listOf<Pair<String,FixTool?>>("AUTO" to null,"PEQ" to FixTool.PEQ,"GEQ" to FixTool.GEQ,"GATE" to FixTool.GATE).forEach { (label,choice) ->
      FilterChip(selected=aiPreference==choice,onClick={aiPreference=choice;resetApproval()},label={Text(label)})
     }
    }
    val evidence = if(liveSession.canUseForAdvice) candidate?.let { FeedbackEvidence(it.hz,it.prominenceDb,it.persistenceMs) } else null
    val advice = evidence?.let { aiEngine.advise(it,mixerAdapter.capabilities(target),aiPreference) }
    Text("Mixer adapter: ${mixerAdapter.family} · capabilities unverified",color=muted)
    Text(advice?.let { "AI recommendation: ${it.tool} / ${it.cutDb} dB / confidence ${it.confidence}%" } ?: "AI recommendation: waiting for detected candidate",color=Color.White)
    Text(advice?.explanation ?: "Analysis source: selected Android input; live routing must be verified",color=muted)
    Text("Gate/Expander is advisory for idle microphone noise, not an active howl cure.",color=muted,style=MaterialTheme.typography.bodySmall)
    Text("DETECTED FEEDBACK",color=gold,fontWeight=FontWeight.Bold)
    Text((if(liveSession.canUseForAdvice) candidate else null)?.let { "${it.hz} Hz  •  +${"%.1f".format(it.prominenceDb)} dB above neighbors  •  ${it.persistenceMs} ms" } ?: "No persistent peak detected",color=Color.White)
    Text("Suggested EQ cut limited to −3 dB from verified baseline; no live writes",color=muted)
    Text("FEEDBACK SUGGESTION QUEUE · v3.7",color=gold,fontWeight=FontWeight.Bold)
    val suggestedCut = advice?.cutDb?.toFloat()
    val available = candidate!=null && liveSession.canUseForAdvice && suggestedCut!=null && suggestedCut<0f && suggestedCut>=-3f
    if(aiMode==AiOperatingMode.ASSIST_CONFIRM) {
     OutlinedButton(enabled=available,onClick={
      val c=candidate
      val cut=suggestedCut
      if(c!=null && cut!=null) {
       // A candidate can be queued once per target/frequency; a new detection needs a new token.
       val id=(target.label+":"+c.hz.toString()).hashCode().toLong()
       if(suggestionQueue.add(FeedbackSuggestion(id,target.label,c.hz.toFloat(),cut))) refreshQueue()
      }
     }) { Text("ADD DETECTED POINT") }
     val queued = suggestionQueue.snapshot()
     if(queued.isEmpty()) Text("No queued frequencies",color=muted)
     queued.forEach { point ->
      Column(Modifier.fillMaxWidth().background(panel,RoundedCornerShape(8.dp)).padding(8.dp)) {
       Text("${point.target} · ${point.hz} Hz · ${point.cutDb} dB",color=if(suggestionQueue.current()?.id==point.id) gold else Color.White)
       Text("${point.status} · preview only",color=muted)
       Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        OutlinedButton(onClick={suggestionQueue.select(point.id);refreshQueue()}) { Text("SELECT") }
        OutlinedButton(enabled=point.status==SuggestionStatus.PENDING,onClick={suggestionQueue.review(point.id);refreshQueue()}) { Text("REVIEW") }
        OutlinedButton(enabled=point.status==SuggestionStatus.REVIEWED,onClick={suggestionQueue.confirmPreview(point.id,true);refreshQueue()}) { Text("CONFIRM") }
       }
       Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        OutlinedButton(enabled=point.status!=SuggestionStatus.PENDING,onClick={suggestionQueue.undo(point.id);refreshQueue()}) { Text("UNDO") }
        OutlinedButton(enabled=point.status==SuggestionStatus.PENDING || point.status==SuggestionStatus.REVIEWED,onClick={suggestionQueue.ignore(point.id);refreshQueue()}) { Text("IGNORE") }
        OutlinedButton(onClick={suggestionQueue.select(point.id);suggestionQueue.next();refreshQueue()}) { Text("NEXT") }
       }
      }
     }
     Text("UNDO resets only this frequency's preview; it does not restore mixer EQ.",color=muted)
    } else {
     Text("Monitor Only: analysis stays active; queue approvals disabled",color=muted)
    }
    @Suppress("UNUSED_VARIABLE") val queueUiRevision = queueRevision
    Button(onClick={ message="GEQ write intentionally disabled until FX-slot OSC mapping and readback are verified" },enabled=false) { Text("APPLY · LOCKED UNTIL VERIFIED") }
    Text("SAFETY: This build never sends GEQ writes. Detector is advisory, not a calibrated feedback suppressor.",color=muted,style=MaterialTheme.typography.bodySmall)
   }
   // Overlay is a sibling of the full-size EQ screen, never a layout column that shrinks it.
   if(advisorVisible) {
    FloatingAiAdvisor(
     mode=aiMode,
     expanded=advisorExpanded,
     points=suggestionQueue.snapshot(),
     currentId=suggestionQueue.current()?.id,
     detectedHz=if(liveSession.canUseForAdvice) candidate?.hz else null,
     onExpand={advisorExpanded=!advisorExpanded},
     onHide={advisorVisible=false},
     onMode={aiMode=it;resetApproval()},
     onSelect={suggestionQueue.select(it);refreshQueue()},
     onReview={if(aiMode==AiOperatingMode.ASSIST_CONFIRM) suggestionQueue.review(it);refreshQueue()},
     onConfirm={suggestionQueue.confirmPreview(it,aiMode==AiOperatingMode.ASSIST_CONFIRM);refreshQueue()},
     onIgnore={if(aiMode==AiOperatingMode.ASSIST_CONFIRM) suggestionQueue.ignore(it);refreshQueue()},
     onUndo={if(aiMode==AiOperatingMode.ASSIST_CONFIRM) suggestionQueue.undo(it);refreshQueue()},
     onNext={suggestionQueue.next();refreshQueue()}
    )
   } else {
    OutlinedButton(onClick={advisorVisible=true},modifier=Modifier.align(Alignment.TopEnd).padding(8.dp)) { Text("AI ADVISOR") }
   }
   }
  }
 }
}

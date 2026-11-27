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

private val bg=Color(0xFF101216)
private val panel=Color(0xFF20242A)
private val gold=Color(0xFFD9AA59)
private val muted=Color(0xFF9CA3AF)
class MainActivity:ComponentActivity() {
 override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE; setContent { Screen() } }
 @Composable private fun Screen() {
  val scope= rememberCoroutineScope()
  var advancedView by remember { mutableStateOf(false) }
  var showGraphSettings by remember { mutableStateOf(false) }
  BackHandler(enabled=advancedView || showGraphSettings) {
   if(advancedView) advancedView=false else showGraphSettings=false
  }
  var settingsTab by remember { mutableStateOf("METERING") }
  var meterbridge by remember { mutableStateOf(false) }
  var meterbridgeFaders by remember { mutableStateOf(true) }
  var meterbridgeLayers by remember { mutableStateOf(true) }
  var meterStyle by remember { mutableStateOf("Smooth") }
  var meterCount by remember { mutableStateOf("Default") }
  var peakHold by remember { mutableStateOf("Inf.") }
  var meterDecay by remember { mutableStateOf("40 dB/sec") }
  var grLevelPlot by remember { mutableStateOf(true) }
  var meterTapInput by remember { mutableStateOf("Preamp") }
  var meterTapMix by remember { mutableStateOf("Post Fader") }

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
  var livePeqBand by remember { mutableIntStateOf(1) }
  var livePeqGain by remember { mutableFloatStateOf(-1f) }
  var livePeqArmed by remember { mutableStateOf(false) }
  var livePeqBusy by remember { mutableStateOf(false) }

  val client= remember { OscClient() }
  var bridgeLevels by remember { mutableStateOf<List<Float>?>(null) }
  val bridgeBus=targetKind==TargetKind.BUS
  LaunchedEffect(meterbridge,connected,bridgeBus) {
   bridgeLevels=null
   if(meterbridge && connected) withContext(Dispatchers.IO) {
    try {
     M32MeterReader().use { reader ->
      var nextRenew=0L
      while(kotlinx.coroutines.currentCoroutineContext().isActive) {
       val now=android.os.SystemClock.elapsedRealtime()
       if(now>=nextRenew){reader.subscribe(bridgeBus);nextRenew=now+5000L}
       val frame=reader.read(bridgeBus)
       withContext(Dispatchers.Main) { bridgeLevels=frame }
      }
     }
    } finally { withContext(kotlinx.coroutines.NonCancellable+Dispatchers.Main) { bridgeLevels=null } }
   }
  }

  val detector= remember { FeedbackDetector() }
  var feedbackThreshold by remember { mutableFloatStateOf(10f) }
  var feedbackMaxCut by remember { mutableFloatStateOf(3f) }
  var feedbackFixed by remember { mutableStateOf(true) }
  var autoNotchPreview by remember { mutableStateOf(false) }
  var caught by remember { mutableStateOf(listOf<Candidate>()) }
  var notchHz by remember { mutableFloatStateOf(2500f) }
  var notchGain by remember { mutableFloatStateOf(1f) }
  var notchQ by remember { mutableFloatStateOf(8f) }
  var notchSelected by remember { mutableStateOf(false) }
  var savedNotches by remember(targetKind,targetNumber) { mutableStateOf(listOf<Triple<Float,Float,Float>>()) }
  var editNotch by remember(targetKind,targetNumber) { mutableIntStateOf(-1) }
  var notchUndo by remember(targetKind,targetNumber) { mutableStateOf<List<Triple<Float,Float,Float>>?>(null) }
  LaunchedEffect(targetKind,targetNumber,detecting,feedbackThreshold,feedbackFixed) {
   caught=emptyList();candidate=null;notchSelected=false;detector.reset()
  }
  var autoBankStatus by remember { mutableStateOf("OFF") }
  var lastAutoInsert by remember(targetKind,targetNumber) { mutableStateOf(0L) }
  LaunchedEffect(candidate,autoNotchPreview,feedbackFixed,targetKind,targetNumber,detecting,feedbackMaxCut) {
   val hit=candidate
   if(!autoNotchPreview || !detecting) {autoBankStatus="OFF";return@LaunchedEffect}
   if(hit==null) {autoBankStatus="LISTENING";return@LaunchedEffect}
   if(notchSelected) {autoBankStatus="EDIT PAUSED";return@LaunchedEffect}
   val hz=hit.hz.toFloat()
   if(!hz.isFinite() || hz !in 80f..12000f) return@LaunchedEffect
   if(savedNotches.any { kotlin.math.abs(kotlin.math.ln(hz/it.first)) < 0.06f }) {autoBankStatus="DUPLICATE HELD";return@LaunchedEffect}
   val now=android.os.SystemClock.elapsedRealtime()
   if(now-lastAutoInsert<1500L) return@LaunchedEffect
   val used=savedNotches.sumOf { minOf(it.second,feedbackMaxCut).toDouble() }
   val cut=minOf(1f,feedbackMaxCut,(6.0-used).toFloat())
   if(cut<0.5f || savedNotches.size>=4) {autoBankStatus="LIMIT REACHED";return@LaunchedEffect}
   notchUndo=savedNotches
   savedNotches=savedNotches+Triple(hz,cut,8f)
   lastAutoInsert=now
   autoBankStatus=if(feedbackFixed) "FIXED PREVIEW ADDED" else "LIVE PREVIEW ADDED"
  }
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
  var useMixerUsbInput by remember { mutableStateOf(false) }
  var useNetworkInput by remember { mutableStateOf(false) }
  var networkRunning by remember { mutableStateOf(false) }
  var useOscSpectrum by remember { mutableStateOf(false) }
  var oscSpectrumRunning by remember { mutableStateOf(false) }
  var spectrumStatus by remember { mutableStateOf("STOPPED") }
  var bridgeHost by remember { mutableStateOf("192.168.0.101") }
  var bridgeStatus by remember { mutableStateOf("NOT CONNECTED") }
  fun switchRtaSource(network:Boolean, usb:Boolean, device:Int?, osc:Boolean=false) {
   val running=detecting || networkRunning || oscSpectrumRunning
   detecting=false;networkRunning=false;oscSpectrumRunning=false;useOscSpectrum=osc
   useNetworkInput=network;useMixerUsbInput=usb;chosenDeviceId=device
   actualDeviceId=null;routingVerified=false;candidate=null;caught=emptyList();detector.reset();resetApproval()
   if(running) {
    if(osc) {oscSpectrumRunning=true;spectrumStatus="WAITING FOR OSC"}
    else if(network) {networkRunning=true;bridgeStatus="CONNECTING"}
    else if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) detecting=true
   }
  }
  val usbInputs = devices.filter { it.usb }
  val actualInputName = devices.firstOrNull { it.id == actualDeviceId }?.name ?: "unknown"


  var rtaHistory by remember { mutableStateOf<List<List<Float>>>(emptyList()) }
  var minInputDbfs by remember { mutableStateOf<Double?>(null) }
  var maxInputDbfs by remember { mutableStateOf<Double?>(null) }
  var rtaMode by remember { mutableStateOf(RtaMode.BAR) }
  var rtaFloor by remember { mutableFloatStateOf(-90f) }
  var rtaStyle by remember { mutableStateOf(RtaStyle.PIXEL_LED) }
  val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (!granted) message="Microphone permission denied" else detecting=true }
  DisposableEffect(Unit) { onDispose { client.close() } }
  LaunchedEffect(detecting,useMixerUsbInput,chosenDeviceId) {
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
     if(useMixerUsbInput && (chosenDeviceId==null || AudioInputManager.available(this@MainActivity).none { it.id==chosenDeviceId && it.usb })) {
      inputWarning="MIXER USB unavailable: connect and select a USB audio interface"
      detecting=false;return@withContext
     }
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
     routingVerified=if(useMixerUsbInput) chosenDeviceId!=null && actualDeviceId==chosenDeviceId else chosenDeviceId==null || actualDeviceId==chosenDeviceId
     sessionGuard.routeVerified(routingVerified)
     if(!routingVerified) { message="Audio routed to unexpected device; analysis stopped"; detecting=false; return@withContext }
     val data=ShortArray(4096)
     while (detecting && kotlinx.coroutines.currentCoroutineContext().isActive) {
      val n=recorder.read(data,0,data.size,AudioRecord.READ_NON_BLOCKING)
      val now=System.currentTimeMillis()
      if(n>0) {
       recovery.read(android.os.SystemClock.elapsedRealtime(),n)
       captureHealth=healthMonitor.accept(data,n,data.size,now)
       if(captureHealth.rmsDbfs.isFinite()) {
        val level=captureHealth.rmsDbfs
        minInputDbfs=minInputDbfs?.let { minOf(it,level) } ?: level
        maxInputDbfs=maxInputDbfs?.let { maxOf(it,level) } ?: level
       }
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
        val next=detector.analyze(frame,System.currentTimeMillis(),feedbackThreshold.toDouble())
        if(next!=null && liveSession.canUseForAdvice) {
         candidate=next
         caught=if(feedbackFixed) (caught.filterNot { it.hz==next.hz }+next).takeLast(8) else listOf(next)
        } else if(!feedbackFixed) {caught=emptyList();candidate=null}
       }
      } else if(n==0) delay(10)
     }
    } catch (e:kotlinx.coroutines.CancellationException) { throw e } catch (e:Exception) { message="Audio error: ${e.message}"; detecting=false }
    finally { recovery.stop(); sessionGuard.end(); liveSession=sessionGuard.status(android.os.SystemClock.elapsedRealtime()); candidate=null; resetApproval(); try { recorder.stop() } catch (_:Exception) {}; recorder.release(); actualDeviceId=null; routingVerified=false }
   }
  }
  LaunchedEffect(oscSpectrumRunning) {
   if(!oscSpectrumRunning) return@LaunchedEffect
   rtaLevels=List(31){-120f};rtaHistory=emptyList();candidate=null
   withContext(Dispatchers.IO) {
    try {
     M32SpectrumProbe().use { probe ->
      var renew=0L
      var last=0L
      while(kotlinx.coroutines.currentCoroutineContext().isActive && oscSpectrumRunning) {
       val now=android.os.SystemClock.elapsedRealtime()
       if(now>=renew){probe.subscribe();renew=now+3000}
       val reply=probe.read()
       if(reply!=null){last=now;withContext(Dispatchers.Main){spectrumStatus=reply}}
       else if(now-last>2000) withContext(Dispatchers.Main){spectrumStatus="WAITING FOR SPECTRUM PACKETS"}
      }
     }
    } catch(e:kotlinx.coroutines.CancellationException){throw e}
    catch(e:Exception){withContext(Dispatchers.Main){spectrumStatus="OSC ERROR: ${e.message}";oscSpectrumRunning=false}}
   }
  }
  LaunchedEffect(networkRunning) {
   if(!networkRunning) return@LaunchedEffect
   withContext(Dispatchers.IO) {
    try {
     Socket().use { socket ->
      socket.connect(java.net.InetSocketAddress(bridgeHost,18777),3000)
      socket.soTimeout=2500
      bridgeStatus="CONNECTED · WAITING FOR PCM"
      val input=socket.getInputStream()
      val pcm=ByteArray(2048)
      val samples=ShortArray(1024)
      val assembler=PcmFrameAssembler(4096)
      while(networkRunning && kotlinx.coroutines.currentCoroutineContext().isActive) {
       var received=0
       while(received<pcm.size) {
        val n=input.read(pcm,received,pcm.size-received)
        if(n<0) throw java.io.EOFException("Bridge disconnected")
        received+=n
       }
       ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
       bridgeStatus="NETWORK AUDIO ACTIVE · PCM 48k"
       captureHealth=AudioCaptureDiagnostics().apply { accept(samples,samples.size,samples.size,System.currentTimeMillis()) }.snapshot()
       assembler.append(samples,samples.size) { frame ->
        val levels=rtaAnalyzer.measure(frame)
        rtaLevels=levels;captureFrames++
        rtaHistory=(rtaHistory+listOf(levels)).takeLast(90)
       }
      }
     }
    } catch(e:kotlinx.coroutines.CancellationException) { throw e } catch(e:Exception) {
     if(useNetworkInput) {bridgeStatus="NETWORK ERROR: ${e.message}";networkRunning=false}
    }
   }
  }
  MaterialTheme(colorScheme=darkColorScheme(primary=gold,background=bg,surface=panel,onBackground=Color.White,onSurface=Color.White)) {
   Box(Modifier.fillMaxSize().background(bg).systemBarsPadding()) {
   if(!advancedView) {
    Column(Modifier.fillMaxSize().padding(8.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
     Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
      Text("KAI Ai GEQ",color=gold,fontWeight=FontWeight.Black,fontSize=13.sp)
      Text(if(connected) "OSC OK" else "OFFLINE",color=if(connected) Color(0xFF58D69C) else muted,fontSize=11.sp)
      Text("EQ LOCKED",color=gold,fontSize=10.sp)
      Spacer(Modifier.weight(1f))
      OutlinedButton(onClick={showGraphSettings=!showGraphSettings},contentPadding=PaddingValues(horizontal=8.dp),modifier=Modifier.height(32.dp)) {Text(if(showGraphSettings) "CLOSE" else "SETTINGS",fontSize=11.sp)}
     }
     Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
      Column(Modifier.weight(4f).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(5.dp)) {
       Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        Button(onClick={
         if(useOscSpectrum) {oscSpectrumRunning=!oscSpectrumRunning;spectrumStatus=if(oscSpectrumRunning) "WAITING FOR OSC" else "STOPPED"}
         else if(useNetworkInput) {networkRunning=!networkRunning;bridgeStatus=if(networkRunning) "CONNECTING" else "STOPPED"}
         else if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) detecting=!detecting
         else permission.launch(Manifest.permission.RECORD_AUDIO)
        },contentPadding=PaddingValues(horizontal=10.dp)) {Text(if(if(useOscSpectrum) oscSpectrumRunning else if(useNetworkInput) networkRunning else detecting) "STOP RTA" else "START RTA")}
        var targetMenuOpen by remember { mutableStateOf(false) }
        Box {
         OutlinedButton(onClick={targetMenuOpen=true},contentPadding=PaddingValues(horizontal=9.dp,vertical=0.dp),modifier=Modifier.height(38.dp)) {
          Text(target.label+" ▾",color=gold,fontSize=12.sp)
         }
         DropdownMenu(expanded=targetMenuOpen,onDismissRequest={targetMenuOpen=false}) {
          TargetKind.entries.forEach { kind ->
           val count=when(kind) { TargetKind.CHANNEL->32; TargetKind.BUS->16; TargetKind.MAIN_LR->1; TargetKind.MATRIX->6 }
           val title=when(kind) { TargetKind.CHANNEL->"CH";TargetKind.BUS->"BUS";TargetKind.MAIN_LR->"LR";TargetKind.MATRIX->"MTX" }
           DropdownMenuItem(text={Text("$title 01–$count")},onClick={
            targetKind=kind;targetNumber=1;eqChoice=EqKind.OFF;resetApproval();targetMenuOpen=false
           })
          }
         }
        }
        var numberMenuOpen by remember { mutableStateOf(false) }
        Box {
         OutlinedButton(onClick={numberMenuOpen=true},contentPadding=PaddingValues(horizontal=7.dp),modifier=Modifier.height(38.dp)) {
          Text(if(targetKind==TargetKind.MAIN_LR) "LR" else "%02d ▾".format(targetNumber),color=gold,fontSize=11.sp)
         }
         DropdownMenu(expanded=numberMenuOpen,onDismissRequest={numberMenuOpen=false},modifier=Modifier.heightIn(max=320.dp)) {
          (1..maxTarget).forEach { number ->
           DropdownMenuItem(text={Text("%02d".format(number))},onClick={targetNumber=number;resetApproval();numberMenuOpen=false})
          }
         }
        }
        if(showGraphSettings) OutlinedButton(onClick={if(targetNumber>1){targetNumber--;resetApproval()}},enabled=targetNumber>1,contentPadding=PaddingValues(horizontal=7.dp),modifier=Modifier.height(38.dp)) {Text("−")}
        if(showGraphSettings) OutlinedButton(onClick={if(targetNumber<maxTarget){targetNumber++;resetApproval()}},enabled=targetNumber<maxTarget,contentPadding=PaddingValues(horizontal=7.dp),modifier=Modifier.height(38.dp)) {Text("+")}
        Text("EQ",color=muted,fontSize=11.sp)
        listOf(EqKind.OFF,EqKind.GEQ,EqKind.PEQ).forEach { kind ->
         FilterChip(selected=eqChoice==kind,onClick={eqChoice=kind;resetApproval()},label={Text(kind.name,fontSize=10.sp)},modifier=Modifier.height(34.dp))
        }
        var inputMenuOpen by remember { mutableStateOf(false) }
        Box {
         OutlinedButton(onClick={devices=AudioInputManager.available(this@MainActivity);inputMenuOpen=true},contentPadding=PaddingValues(horizontal=8.dp),modifier=Modifier.height(38.dp)) {
          Text(if(useOscSpectrum) "OSC ▾" else if(useNetworkInput) "NET ▾" else if(useMixerUsbInput) "USB ▾" else "MIC ▾",fontSize=11.sp,color=gold)
         }
         DropdownMenu(expanded=inputMenuOpen,onDismissRequest={inputMenuOpen=false}) {
          DropdownMenuItem(text={Text("PHONE MIC · Android default")},onClick={
           switchRtaSource(false,false,null);inputMenuOpen=false
          })
          HorizontalDivider()
          DropdownMenuItem(text={Text("M32 NETWORK (PC) · EVO4 bridge")},onClick={switchRtaSource(true,false,null);inputMenuOpen=false})
          DropdownMenuItem(text={Text("M32 OSC · Spectrum diagnostic (no PC)")},onClick={switchRtaSource(false,false,null,true);inputMenuOpen=false})
          HorizontalDivider()
          if(usbInputs.isEmpty()) {
           DropdownMenuItem(text={Text("MIXER USB · no USB input detected")},enabled=false,onClick={})
          } else {
           usbInputs.forEach { device ->
            DropdownMenuItem(text={Text("MIXER USB · ${device.name} (#${device.id})")},onClick={
             switchRtaSource(false,true,device.id);inputMenuOpen=false
            })
           }
          }
         }
        }
        if(showGraphSettings) Text(if(useNetworkInput) bridgeStatus else if(detecting) {
         if(routingVerified) "ACTIVE: ${if(useMixerUsbInput) "USB" else "PHONE"} · $actualInputName" else "INPUT ROUTE UNVERIFIED"
        } else if(useMixerUsbInput) "USB SELECTED · NOT CAPTURING" else "PHONE SELECTED · NOT CAPTURING",color=if(detecting && routingVerified) Color(0xFF58D69C) else muted,fontSize=9.sp,maxLines=1)
        if(useNetworkInput && showGraphSettings) {
         OutlinedTextField(value=bridgeHost,onValueChange={bridgeHost=it},singleLine=true,label={Text("PC IP",fontSize=10.sp)},modifier=Modifier.width(145.dp).height(54.dp))
        }

       }
       if(useOscSpectrum) Text("OSC RTA TEST · $spectrumStatus",color=gold,fontSize=10.sp)
       if(meterbridge && showGraphSettings) {
        Column(Modifier.fillMaxWidth().background(panel,RoundedCornerShape(5.dp)).padding(4.dp)) {
         Text(if(bridgeLevels==null) "M32 METERBRIDGE · NO VALID DATA" else "M32 METERBRIDGE · "+if(bridgeBus) "BUS" else "CH",color=gold,fontSize=10.sp)
         val count=(meterCount.toIntOrNull() ?: if(bridgeBus) 16 else 32).coerceAtMost(if(bridgeBus) 16 else 32)
         val offset=if(meterbridgeLayers) (((targetNumber-1)/count)*count).coerceAtMost((if(bridgeBus) 16 else 32)-count) else 0
         Row(Modifier.fillMaxWidth().height(42.dp),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
          (0 until count).forEach { index ->
           val channel=offset+index
           val db=bridgeLevels?.getOrNull(channel)
           Column(Modifier.weight(1f).fillMaxHeight().clickable {targetKind=if(bridgeBus) TargetKind.BUS else TargetKind.CHANNEL;targetNumber=channel+1;resetApproval()},horizontalAlignment=Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).width(8.dp).background(Color(0xFF101216)),contentAlignment=Alignment.BottomCenter) {
             if(db!=null) Box(Modifier.fillMaxWidth().fillMaxHeight(((db+60f)/60f).coerceIn(0f,1f)).background(if(db>=0f) Color.Red else if(db>=-12f) Color.Yellow else Color(0xFF58D69C)))
            }
            Text((channel+1).toString(),color=if(targetNumber==channel+1) gold else muted,fontSize=9.sp)
           }
          }
         }
        }
       }
       Column(Modifier.weight(1f).fillMaxWidth().background(panel,RoundedCornerShape(7.dp)).padding(5.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
         Text(when(eqChoice) { EqKind.GEQ -> "LIVE RTA + 31-BAND GEQ · PREVIEW"; EqKind.PEQ -> "LIVE RTA + PEQ · ${target.peqBands} BANDS"; EqKind.OFF -> "LIVE RTA · EQ OVERLAY OFF" },color=gold,style=MaterialTheme.typography.labelSmall)
         var quickMeterOpen by remember { mutableStateOf(false) }
         Box {
          OutlinedButton(onClick={quickMeterOpen=true},modifier=Modifier.height(28.dp),contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp)) {Text("RTA SETTINGS",fontSize=10.sp)}
          DropdownMenu(expanded=quickMeterOpen,onDismissRequest={quickMeterOpen=false},modifier=Modifier.width(290.dp).heightIn(max=410.dp)) {
           Text("METERING",color=gold,fontWeight=FontWeight.Bold,modifier=Modifier.padding(10.dp))
           listOf("Meterbridge","Meterbridge Faders","Use Layers","GR level plot").forEach { name ->
            val active=when(name) { "Meterbridge"->meterbridge;"Meterbridge Faders"->meterbridgeFaders;"Use Layers"->meterbridgeLayers;else->grLevelPlot }
            DropdownMenuItem(text={Text(name,fontSize=12.sp)},trailingIcon={Text(if(active) "ON" else "OFF",color=if(active) gold else muted)},onClick={
             when(name) {"Meterbridge"->meterbridge=!meterbridge;"Meterbridge Faders"->meterbridgeFaders=!meterbridgeFaders;"Use Layers"->meterbridgeLayers=!meterbridgeLayers;else->grLevelPlot=!grLevelPlot}
            })
           }
           listOf("Smooth","Pixel LED").forEach { style ->
            DropdownMenuItem(text={Text("Style: $style",fontSize=12.sp)},trailingIcon={if(meterStyle==style) Text("✓",color=gold)},onClick={meterStyle=style;rtaStyle=if(style=="Smooth") RtaStyle.SMOOTH else RtaStyle.PIXEL_LED})
           }
           listOf("Off","1 sec","3 sec","Inf.").forEach { choice ->
            DropdownMenuItem(text={Text("Peak Hold: $choice",fontSize=12.sp)},trailingIcon={if(peakHold==choice) Text("✓",color=gold)},onClick={peakHold=choice})
           }
           listOf("20 dB/sec","40 dB/sec","60 dB/sec").forEach { choice ->
            DropdownMenuItem(text={Text("Decay: $choice",fontSize=12.sp)},trailingIcon={if(meterDecay==choice) Text("✓",color=gold)},onClick={meterDecay=choice})
           }
           DropdownMenuItem(text={Text("Meter Count / Tap Points / More…",fontSize=12.sp,color=gold)},onClick={quickMeterOpen=false;settingsTab="METERING";advancedView=true})
           Text("Only meter style currently changes RTA rendering. Other options are UI preferences until meter processing and M32 tap-point support are implemented.",color=muted,fontSize=10.sp,modifier=Modifier.padding(10.dp))
          }
         }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
         RtaDisplay(rtaLevels,rtaHistory,rtaMode,rtaFloor,rtaStyle,Modifier.fillMaxSize(),peakHold,meterDecay)
         if(eqChoice==EqKind.PEQ) {
          val snapshot=peqSnapshot?.takeIf { it.target==target && it.complete }
          val points=(1..target.peqBands).mapNotNull { band ->
           fun raw(key:String)=snapshot?.values?.entries?.firstOrNull { it.key.endsWith("/$band/$key") }?.value?.firstOrNull() as? Float
           val f=raw("f");val g=raw("g")
           if(f!=null && g!=null && f.isFinite() && g.isFinite() && f in 0f..1f && g in 0f..1f) Pair(20.0*Math.pow(1000.0,f.toDouble()),g*30f-15f) else null
          }
          androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
           val zero=size.height*0.25f
           points.forEach { (hz,db) ->
            val x=(kotlin.math.log10(hz/20.0)/3.0).toFloat()*size.width
            val y=(zero-db*size.height/18f).coerceIn(0f,size.height)
            drawCircle(Color(0xFF58D69C),radius=4.dp.toPx(),center=androidx.compose.ui.geometry.Offset(x,y))
           }
           candidate?.hz?.takeIf { it.isFinite() && it in 20.0..20000.0 }?.let { hz ->
            val x=(kotlin.math.log10(hz/20.0)/3.0).toFloat()*size.width
            drawLine(Color(0xFFFF5252),androidx.compose.ui.geometry.Offset(x,0f),androidx.compose.ui.geometry.Offset(x,size.height),strokeWidth=1.5.dp.toPx())
           }
          }
          Text("GREEN: M32 F/G estimates · RED: tonal candidate",color=Color(0xFF58D69C),fontSize=9.sp,modifier=Modifier.align(Alignment.BottomStart))
         }
         if(eqChoice==EqKind.PEQ && (savedNotches.isNotEmpty() || notchSelected)) {
          val bands=savedNotches.filterIndexed { i,_ -> !notchSelected || i!=editNotch } + if(notchSelected) listOf(Triple(notchHz,notchGain,notchQ)) else emptyList()
          androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
           val mid=size.height*0.25f
           drawLine(Color(0x88D9AA59),androidx.compose.ui.geometry.Offset(0f,mid),androidx.compose.ui.geometry.Offset(size.width,mid))
           val curve=androidx.compose.ui.graphics.Path()
           for(i in 0..240) {
            val f=20.0*Math.pow(1000.0,i/240.0)
            val db=bands.sumOf { b ->
             val a=Math.pow(10.0,-minOf(b.second,feedbackMaxCut)/40.0)
             val v=b.third*(f/b.first-b.first/f)
             10*kotlin.math.log10((v*v+a*a)/(v*v+1/(a*a)))
            }
            val x=size.width*i/240f
            val y=mid-db.toFloat()*size.height/18f
            if(i==0) curve.moveTo(x,y) else curve.lineTo(x,y)
           }
           drawPath(curve,Color(0xFFD9AA59),style=androidx.compose.ui.graphics.drawscope.Stroke(width=2.dp.toPx()))
          }
          Text("LOCAL PEQ PREVIEW · relative dB",color=gold,fontSize=9.sp,modifier=Modifier.align(Alignment.TopEnd))
         }
         if(eqChoice==EqKind.GEQ) {
         Row(Modifier.fillMaxSize().padding(top=6.dp,bottom=4.dp),horizontalArrangement=Arrangement.spacedBy(1.dp)) {
          detector.centers.forEachIndexed { index,hz ->
           Box(Modifier.weight(1f).fillMaxHeight(),contentAlignment=Alignment.Center) {
            Box(Modifier.width(1.dp).fillMaxHeight(0.86f).background(Color(0x665D6875)))
            Box(Modifier.fillMaxWidth(0.88f).height(7.dp).background(Color(0xFFC1C8D0),RoundedCornerShape(2.dp)))
           }
          }
         }
         }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(1.dp)) {
         detector.centers.forEach { hz ->
          val frequencyLabel=when {
           hz==31.5 -> "31.5"
           hz<1000.0 -> hz.toInt().toString()
           hz==1250.0 -> "1k25"
           hz==3150.0 -> "3k15"
           hz==6300.0 -> "6k3"
           hz==12500.0 -> "12k5"
           else -> "${(hz/1000.0).toString().removeSuffix(".0")}k"
          }
          Box(Modifier.weight(1f).height(16.dp),contentAlignment=Alignment.TopCenter) {
           Text(frequencyLabel,color=Color(0xFFD0D4D8),fontSize=7.sp,maxLines=1,softWrap=false)
          }
         }
        }
       }
      }
      if(showGraphSettings) Column(Modifier.weight(2.5f).fillMaxHeight().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(4.dp)) {
       OutlinedButton(onClick={advancedView=true},modifier=Modifier.fillMaxWidth()) {Text("ADVANCED SETTINGS",fontSize=11.sp)}
       ClassicDbMeter(captureHealth.rmsDbfs,captureHealth.peakDbfs,minInputDbfs,maxInputDbfs,
        onReset={minInputDbfs=null;maxInputDbfs=null},modifier=Modifier.fillMaxWidth().height(110.dp))
       Column(Modifier.fillMaxWidth().background(panel,RoundedCornerShape(7.dp)).padding(5.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
       Text("FEEDBACK · CAUGHT",color=gold,fontWeight=FontWeight.Bold)
       Text("THRESHOLD +${feedbackThreshold.toInt()} dB",color=Color.White,fontSize=10.sp)
       Slider(value=feedbackThreshold,onValueChange={feedbackThreshold=it},valueRange=6f..24f,steps=17,modifier=Modifier.height(28.dp))
       Text("MAX CUT PREVIEW −${feedbackMaxCut.toInt()} dB",color=Color.White,fontSize=10.sp)
       Slider(value=feedbackMaxCut,onValueChange={feedbackMaxCut=it},valueRange=1f..3f,steps=1,modifier=Modifier.height(28.dp))
       Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        FilterChip(selected=feedbackFixed,onClick={feedbackFixed=true},label={Text("FIXED",fontSize=10.sp)})
        FilterChip(selected=!feedbackFixed,onClick={feedbackFixed=false},label={Text("LIVE",fontSize=10.sp)})
       }
       Row(verticalAlignment=Alignment.CenterVertically) {
        Switch(checked=autoNotchPreview,onCheckedChange={autoNotchPreview=it})
        Text("AUTO NOTCH PREVIEW",color=gold,fontSize=10.sp)
        Text(autoBankStatus,color=muted,fontSize=9.sp)
       }
       caught.forEach { hit ->
        OutlinedButton(onClick={notchHz=hit.hz.toFloat().coerceIn(80f,12000f);notchGain=1f;editNotch=-1;notchSelected=true},contentPadding=PaddingValues(horizontal=5.dp)) {Text("SELECT ${hit.hz.toInt()} Hz",fontSize=10.sp)}
        Text("${hit.hz} Hz · +${"%.1f".format(hit.prominenceDb)} dB"+if(autoNotchPreview) " · proposed −${minOf(feedbackMaxCut.toDouble(),kotlin.math.abs(hit.suggestedCutDb))} dB" else "",color=Color.White,fontSize=10.sp)
       }
       OutlinedButton(onClick={caught=emptyList();candidate=null;detector.reset()},contentPadding=PaddingValues(horizontal=6.dp)) {Text("RESET CAUGHT",fontSize=10.sp)}
       Text("M32 PEQ · READ ONLY",color=gold,fontSize=10.sp)
       var readBusy by remember { mutableStateOf(false) }
       var readError by remember { mutableStateOf("") }
       OutlinedButton(enabled=connected&&!readBusy,onClick={
        readBusy=true;readError="";peqSnapshot=null
        val requested=target
        scope.launch {
         try {
          val result=withContext(Dispatchers.IO) {
           val readerSocket=OscClient()
           try {readerSocket.connect();M32LiveStateReader(readerSocket).inspectPeq(requested)}
           finally {readerSocket.close()}
          }
          peqSnapshot=result
         } catch(e:Exception) {readError=e.message?:"Read failed"}
         finally {readBusy=false}
        }
       }) {Text(if(readBusy) "READING..." else "READ M32 PEQ",fontSize=10.sp)}
       if(readError.isNotEmpty()) Text(readError,color=muted,fontSize=10.sp)
       peqSnapshot?.takeIf { it.target==target }?.let { snapshot ->
        Text(if(snapshot.complete) "Snapshot received · estimated F/G; Q raw" else "Partial snapshot · ${snapshot.missing.size} missing",color=muted,fontSize=10.sp)
        fun raw(suffix:String):String = snapshot.values.entries.firstOrNull { it.key.endsWith(suffix) }?.value?.joinToString() ?: "?"
        Text("EQ ON: ${raw("/on")}",color=Color.White,fontSize=10.sp)
        for(band in 1..target.peqBands) {
         val gainRaw=snapshot.values.entries.firstOrNull { it.key.endsWith("/$band/g") }?.value?.firstOrNull() as? Float
         val freqRaw=snapshot.values.entries.firstOrNull { it.key.endsWith("/$band/f") }?.value?.firstOrNull() as? Float
         val gainDb=gainRaw?.takeIf { it.isFinite() && it in 0f..1f }?.let { it*30f-15f }
         val freqHz=freqRaw?.takeIf { it.isFinite() && it in 0f..1f }?.let { 20.0*Math.pow(1000.0,it.toDouble()) }
         Text("B$band · T:${raw("/$band/type")} · ${freqHz?.let { "%.0f Hz".format(it) } ?: "F unknown"} · ${gainDb?.let { "%+.1f dB".format(it) } ?: "G unknown"}",color=Color.White,fontSize=10.sp)
         Text("Q raw: ${raw("/$band/q")} · ${if(gainDb!=null && kotlin.math.abs(gainDb)<0.1f) "Flat gain only — reserved" else "Preserve existing band"}",color=muted,fontSize=9.sp)
        }
        Text("No band is assumed free. No automatic writes.",color=muted,fontSize=10.sp)
       }
       Text("LOCAL NOTCH BANK ${savedNotches.size}/4",color=gold,fontSize=10.sp)
       savedNotches.forEachIndexed { index, band ->
        Row(verticalAlignment=Alignment.CenterVertically) {
         Text("${band.first.toInt()} Hz / −${minOf(band.second,feedbackMaxCut)} dB / Q ${band.third}",color=Color.White,fontSize=10.sp,modifier=Modifier.weight(1f))
         TextButton(onClick={editNotch=index;notchHz=band.first;notchGain=band.second;notchQ=band.third;notchSelected=true}) {Text("EDIT",fontSize=10.sp)}
         TextButton(onClick={notchUndo=savedNotches;savedNotches=savedNotches.filterIndexed { i,_ -> i!=index };notchSelected=false;editNotch=-1}) {Text("DEL",fontSize=10.sp)}
        }
       }
       TextButton(enabled=notchUndo!=null,onClick={notchUndo?.let { savedNotches=it };notchUndo=null;notchSelected=false;editNotch=-1}) {Text("UNDO BANK")}
       if(notchSelected) {
        Text("PEQ NOTCH · LOCAL PREVIEW ONLY",color=gold,fontSize=11.sp)
        Text("FREQ ${notchHz.toInt()} Hz",color=Color.White,fontSize=10.sp)
        Slider(value=kotlin.math.ln(notchHz),onValueChange={notchHz=kotlin.math.exp(it)},valueRange=kotlin.math.ln(80f)..kotlin.math.ln(12000f),modifier=Modifier.height(28.dp))
        Text("GAIN −${"%.1f".format(minOf(notchGain,feedbackMaxCut))} dB",color=Color.White,fontSize=10.sp)
        Slider(value=minOf(notchGain,feedbackMaxCut),onValueChange={notchGain=it},valueRange=0f..feedbackMaxCut,modifier=Modifier.height(28.dp))
        Text("Q ${"%.1f".format(notchQ)}",color=Color.White,fontSize=10.sp)
        Slider(value=notchQ,onValueChange={notchQ=it},valueRange=1f..10f,modifier=Modifier.height(28.dp))
        Text("Approx. bandwidth ${(notchHz/notchQ).toInt()} Hz · not applied",color=muted,fontSize=10.sp)
        OutlinedButton(enabled=editNotch in savedNotches.indices || savedNotches.size<4,onClick={
         notchUndo=savedNotches
         val band=Triple(notchHz,minOf(notchGain,feedbackMaxCut),notchQ)
         savedNotches=if(editNotch in savedNotches.indices) savedNotches.mapIndexed { i,b -> if(i==editNotch) band else b } else savedNotches+band
         notchSelected=false;editNotch=-1
        }) {Text("SAVE LOCAL NOTCH",fontSize=10.sp)}
        OutlinedButton(onClick={notchSelected=false;editNotch=-1}) {Text("CLEAR PREVIEW",fontSize=10.sp)}
       }
       Text("RMS ${"%.1f".format(captureHealth.rmsDbfs)} / PEAK ${"%.1f".format(captureHealth.peakDbfs)} dBFS",color=Color.White,style=MaterialTheme.typography.labelSmall)
       LinearProgressIndicator(progress=((captureHealth.rmsDbfs+60.0)/60.0).coerceIn(0.0,1.0).toFloat(),modifier=Modifier.fillMaxWidth())
       Text(when { !detecting -> "RTA stopped · start microphone capture"; !liveSession.canUseForAdvice -> "Checking audio frames / input route"; candidate!=null -> "DETECTED: ${candidate!!.hz} Hz"; else -> "Audio verified · listening for persistent feedback" },color=if(liveSession.canUseForAdvice) Color(0xFF58D69C) else muted,style=MaterialTheme.typography.bodySmall)
       Text("Mode: ${aiMode.name}",color=gold)
       val current=suggestionQueue.current()
       Text(current?.let{"${it.hz} Hz  ${it.cutDb} dB  ${it.status}"} ?: "No pending suggestion",color=Color.White)
       Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick={suggestionQueue.next();refreshQueue()},enabled=suggestionQueue.snapshot().isNotEmpty(),contentPadding=PaddingValues(horizontal=6.dp)) {Text("NEXT")}
        OutlinedButton(onClick={current?.let{suggestionQueue.undo(it.id);refreshQueue()}},enabled=current!=null && aiMode==AiOperatingMode.ASSIST_CONFIRM,contentPadding=PaddingValues(horizontal=6.dp)) {Text("UNDO")}
       }
       Text("CONFIRM = PREVIEW ONLY · NO M32 EQ WRITES",color=muted,style=MaterialTheme.typography.labelSmall)
       OutlinedButton(onClick={advancedView=true},contentPadding=PaddingValues(horizontal=6.dp,vertical=2.dp)) {Text("REVIEW / CONFIRM",fontSize=11.sp)}
       }
      }
     }
    }
   } else {
   Column(Modifier.fillMaxSize().padding(8.dp)) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
     OutlinedButton(onClick={advancedView=false},contentPadding=PaddingValues(horizontal=10.dp)) {Text("‹ BACK")}
     listOf("APP","LAYERS","LAYOUTS","CHANNEL STRIP","MIDI","SAVE/LOAD").forEach { tab ->
      FilterChip(selected=settingsTab==tab || (tab=="APP" && settingsTab in listOf("GENERAL","MIXER","METERING","APP-LINK")),onClick={settingsTab=if(tab=="APP") "METERING" else tab},label={Text(tab,fontSize=11.sp)})
     }
    }
    Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     Column(Modifier.width(116.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
      listOf("GENERAL","MIXER","METERING","APP-LINK").forEach { item ->
       FilterChip(selected=settingsTab==item,onClick={settingsTab=item},label={Text(item,fontSize=12.sp)},modifier=Modifier.fillMaxWidth())
      }
     }
     Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(6.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
      if(settingsTab=="METERING") {
       Text("APP SETUP  /  METERING",color=gold,fontWeight=FontWeight.Bold)
       Text("General",color=Color.White)
       Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        @Composable fun meterSwitch(title:String,value:Boolean,change:(Boolean)->Unit) {
         Column(Modifier.width(110.dp).background(panel,RoundedCornerShape(5.dp)).padding(6.dp),horizontalAlignment=Alignment.CenterHorizontally) {
          Text(title,color=muted,fontSize=11.sp,maxLines=2)
          Switch(checked=value,onCheckedChange=change)
         }
        }
        meterSwitch("Meterbridge",meterbridge,{meterbridge=it})
        meterSwitch("Meterbridge Faders",meterbridgeFaders,{meterbridgeFaders=it})
        meterSwitch("Use Layers",meterbridgeLayers,{meterbridgeLayers=it})
        meterSwitch("GR level plot",grLevelPlot,{grLevelPlot=it})
       }
       Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        @Composable fun meterSelect(title:String,value:String,options:List<String>,change:(String)->Unit) {
         var expanded by remember { mutableStateOf(false) }
         Column(Modifier.width(150.dp).background(panel,RoundedCornerShape(5.dp)).padding(6.dp)) {
          Text(title,color=muted,fontSize=11.sp)
          Box {
           OutlinedButton(onClick={expanded=true}) {Text(value,fontSize=11.sp)}
           DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
            options.forEach { option -> DropdownMenuItem(text={Text(option)},onClick={change(option);expanded=false}) }
           }
          }
         }
        }
        meterSelect("Meter style",meterStyle,listOf("Smooth","Pixel LED"),{meterStyle=it;rtaStyle=if(it=="Smooth") RtaStyle.SMOOTH else RtaStyle.PIXEL_LED})
        meterSelect("Meter Count",meterCount,listOf("Default","8","16","32"),{meterCount=it})
        meterSelect("Peak Hold",peakHold,listOf("Off","1 sec","3 sec","Inf."),{peakHold=it})
        meterSelect("Decay",meterDecay,listOf("20 dB/sec","40 dB/sec","60 dB/sec"),{meterDecay=it})
       }
       HorizontalDivider(color=Color.DarkGray)
       Text("Tap points (mixer specific)",color=Color.White)
       Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        listOf("Input","Aux","FX Return","Mix","Group","Matrix","Main").forEach { title ->
         val value=if(title=="Input") meterTapInput else meterTapMix
         var expanded by remember { mutableStateOf(false) }
         Column(Modifier.width(120.dp).background(panel,RoundedCornerShape(5.dp)).padding(6.dp)) {
          Text(title,color=muted,fontSize=11.sp)
          Box {
           OutlinedButton(onClick={expanded=true}) {Text(value,fontSize=11.sp)}
           DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
            listOf("Preamp","Pre Fader","Post Fader").forEach { option ->
             DropdownMenuItem(text={Text(option)},onClick={if(title=="Input") meterTapInput=option else meterTapMix=option;expanded=false})
            }
           }
          }
         }
        }
       }
       Text("Metering options are local UI previews. Tap points do not change M32 routing; no EQ writes.",color=gold,fontSize=12.sp)
       Text("Live Android microphone: RMS ${"%.1f".format(captureHealth.rmsDbfs)} / Peak ${"%.1f".format(captureHealth.peakDbfs)} dBFS",color=Color.White)
      } else if(settingsTab=="MIXER" || settingsTab=="GENERAL") {

    OutlinedButton(onClick={advancedView=false}) { Text("BACK TO LANDSCAPE DASHBOARD") }
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
    Text("MIC / USB INPUT LEVEL · dBFS",color=gold)
    Text("RMS ${"%.1f".format(captureHealth.rmsDbfs)} dBFS · PEAK ${"%.1f".format(captureHealth.peakDbfs)} dBFS",color=Color.White)
    LinearProgressIndicator(
     progress={((captureHealth.rmsDbfs + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat()},
     modifier=Modifier.fillMaxWidth().height(12.dp),
     color=if(captureHealth.peakDbfs > -3f) Color(0xFFFF6B5E) else Color(0xFF37CFA4),
     trackColor=Color(0xFF30343B)
    )
    Text("−60                         −30                         0 dBFS",color=muted,style=MaterialTheme.typography.labelSmall)
    Text("Silent ${captureHealth.silentBlocks} · Clipped ${captureHealth.clippedBlocks} · Not calibrated dB SPL",color=muted,style=MaterialTheme.typography.bodySmall)
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
    RtaDisplay(rtaLevels,rtaHistory,rtaMode,rtaFloor,rtaStyle,Modifier.fillMaxWidth().height(210.dp),peakHold,meterDecay)
     RtaFrequencyAxis(Modifier.fillMaxWidth())
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

    HorizontalDivider(color=Color.DarkGray)
    Text("MANUAL PEQ GAIN WRITE · LIVE M32",color=gold,fontWeight=FontWeight.Bold)
    Text("Only one PEQ band at a time · cut 0 to −3 dB · baseline read + readback required",color=muted)
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     OutlinedButton(onClick={livePeqBand=(livePeqBand-1).coerceAtLeast(1);livePeqArmed=false},enabled=livePeqBand>1) {Text("−")}
     Text("BAND $livePeqBand / ${target.peqBands}",color=Color.White)
     OutlinedButton(onClick={livePeqBand=(livePeqBand+1).coerceAtMost(target.peqBands);livePeqArmed=false},enabled=livePeqBand<target.peqBands) {Text("+")}
     Text("GAIN ${"%.1f".format(livePeqGain)} dB",color=gold)
    }
    Slider(value=livePeqGain,onValueChange={livePeqGain=it;livePeqArmed=false},valueRange=-3f..0f,steps=5)
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     Checkbox(checked=livePeqArmed,onCheckedChange={livePeqArmed=it},enabled=connected&&!livePeqBusy)
     Text("I confirm live PEQ change on ${target.label}",color=Color.White)
     Button(enabled=connected&&livePeqArmed&&!livePeqBusy,onClick={
      livePeqBusy=true;livePeqArmed=false
      val requestedTarget=target
      val requestedBand=livePeqBand
      val requestedGain=livePeqGain
      scope.launch(Dispatchers.IO) {
       try {
        val ok=M32ManualPeqWriter(client).writeGainAndVerify(requestedTarget,requestedBand,requestedGain)
        message=if(ok) "PEQ WRITE VERIFIED: ${requestedTarget.label} band $requestedBand gain $requestedGain dB" else "PEQ WRITE NOT VERIFIED: inspect mixer immediately"
       } catch(e:Exception) { message="PEQ write aborted: ${e.message}" }
       finally {livePeqBusy=false}
      }
     }) {Text(if(livePeqBusy) "WRITING" else "WRITE PEQ GAIN")}
    }
    Text(message,color=gold)
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
      } else {
       Text("$settingsTab · settings preview",color=gold,fontWeight=FontWeight.Bold)
       Text("No live mixer configuration writes are supported in this section.",color=muted)
      }
     }
    }
   }
   }
   // Overlay is a sibling of the full-size EQ screen, never a layout column that shrinks it.
   if(advancedView && advisorVisible) {
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
   } else if(advancedView) {
    OutlinedButton(onClick={advisorVisible=true},modifier=Modifier.align(Alignment.TopEnd).padding(8.dp)) { Text("AI ADVISOR") }
   }
   }
  }
 }
}

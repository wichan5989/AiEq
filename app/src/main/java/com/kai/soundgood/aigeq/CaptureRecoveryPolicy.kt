package com.kai.soundgood.aigeq

/** Pure state machine. Never silently switch to a different audio input. */
enum class CapturePhase { IDLE, STARTING, RUNNING, LOST, WAITING_FOR_DEVICE }
data class RecoveryStatus(val phase: CapturePhase, val detail: String, val mayRestart: Boolean)
class CaptureRecoveryPolicy(private val startupTimeoutMs: Long = 1500, private val stallTimeoutMs: Long = 1500) {
    init { require(startupTimeoutMs > 0 && stallTimeoutMs > 0) }
    private var phase = CapturePhase.IDLE
    private var startedAt = 0L
    private var lastReadAt: Long? = null
    private var selectedDevice: Int? = null
    fun start(nowMs: Long, deviceId: Int?) {
        phase = CapturePhase.STARTING; startedAt = nowMs; lastReadAt = null; selectedDevice = deviceId
    }
    fun read(nowMs: Long, sampleCount: Int) {
        if (sampleCount > 0 && (phase == CapturePhase.STARTING || phase == CapturePhase.RUNNING)) {
            lastReadAt = nowMs; phase = CapturePhase.RUNNING
        }
    }
    fun check(nowMs: Long, routedDevice: Int?, selectedDevicePresent: Boolean = true): RecoveryStatus {
        if (phase == CapturePhase.STARTING || phase == CapturePhase.RUNNING) {
            if (selectedDevice != null && (!selectedDevicePresent || routedDevice != selectedDevice)) {
                phase = CapturePhase.WAITING_FOR_DEVICE
                return status("Selected USB/input device disconnected or route changed")
            }
            if (lastReadAt == null && nowMs - startedAt > startupTimeoutMs) {
                phase = CapturePhase.LOST; return status("No PCM received after capture start")
            }
            if (lastReadAt != null && nowMs - lastReadAt!! > stallTimeoutMs) {
                phase = CapturePhase.LOST; return status("PCM capture stalled")
            }
        }
        return status(when (phase) {
            CapturePhase.IDLE -> "Capture stopped"
            CapturePhase.STARTING -> "Waiting for first PCM frame"
            CapturePhase.RUNNING -> "PCM capture healthy"
            CapturePhase.LOST -> "Capture stopped: manual restart required"
            CapturePhase.WAITING_FOR_DEVICE -> "Reconnect selected device, then restart"
        })
    }
    fun stop() { phase = CapturePhase.IDLE; lastReadAt = null }
    private fun status(detail: String) = RecoveryStatus(phase, detail, phase == CapturePhase.LOST || phase == CapturePhase.WAITING_FOR_DEVICE || phase == CapturePhase.IDLE)
}

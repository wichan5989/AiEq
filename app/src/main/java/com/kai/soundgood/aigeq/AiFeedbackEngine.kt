package com.kai.soundgood.aigeq

/** Platform-independent policy: Android now, desktop and additional mixers later.
 *  This is a deterministic decision assistant, NOT a trained neural network.
 */
enum class MixerFamily { MIDAS_M32, ALLEN_HEATH_QU, BEHRINGER_M_AIR }
enum class FixTool { PEQ, GEQ, GATE, OBSERVE }
enum class EvidenceSource { ANDROID_MIC, USB_AUDIO, VERIFIED_MIXER_RTA, SIMULATED }
data class FeedbackEvidence(
    val frequencyHz: Double,
    val prominenceDb: Double,
    val persistenceMs: Long,
    val risingDbPerSecond: Double = 0.0,
    val speechLikely: Boolean = false,
    val source: EvidenceSource = EvidenceSource.ANDROID_MIC
)
data class MixerCapabilities(
    val peqVerified: Boolean = false,
    val geqVerified: Boolean = false,
    val gateVerified: Boolean = false,
    val fxRoutingVerified: Boolean = false,
    val channelInputVerified: Boolean = false
)
data class FeedbackAdvice(
    val tool: FixTool,
    val frequencyHz: Double,
    val cutDb: Double,
    val confidence: Int,
    val explanation: String,
    val requiresConfirmation: Boolean = true
)
interface MixerAdapter {
    val family: MixerFamily
    fun capabilities(target: EqTarget): MixerCapabilities
    /** No implementation is allowed to write without explicit verified state. */
    fun canWrite(target: EqTarget, tool: FixTool): Boolean
}
class M32Adapter : MixerAdapter {
    override val family = MixerFamily.MIDAS_M32
    override fun capabilities(target: EqTarget) = MixerCapabilities()
    override fun canWrite(target: EqTarget, tool: FixTool) = false
}
class UnsupportedMixerAdapter(override val family: MixerFamily) : MixerAdapter {
    override fun capabilities(target: EqTarget) = MixerCapabilities()
    override fun canWrite(target: EqTarget, tool: FixTool) = false
}
class AiFeedbackEngine(private val maximumCutDb: Double = 3.0) {
    init { require(maximumCutDb in 0.0..3.0) }
    fun advise(evidence: FeedbackEvidence, capabilities: MixerCapabilities, prefer: FixTool? = null): FeedbackAdvice {
        val persistent = evidence.persistenceMs >= 700 && evidence.prominenceDb >= 10.0
        val confidence = (if (persistent) 70 else 25) +
            (evidence.prominenceDb - 10.0).coerceIn(0.0, 15.0).toInt() +
            (evidence.persistenceMs / 500).toInt().coerceIn(0, 10) -
            (if (evidence.speechLikely) 35 else 0)
        val score = confidence.coerceIn(0, 95)
        if (!persistent || evidence.speechLikely || score < 75) return FeedbackAdvice(
            FixTool.OBSERVE, evidence.frequencyHz, 0.0, score,
            "Insufficient evidence or likely speech/music harmonic; observe without changing mixer DSP"
        )
        val choice = when {
            prefer == FixTool.PEQ && capabilities.peqVerified -> FixTool.PEQ
            prefer == FixTool.GEQ && capabilities.geqVerified && capabilities.fxRoutingVerified -> FixTool.GEQ
            prefer == FixTool.GATE -> FixTool.OBSERVE // Gate cannot reliably stop open-mic feedback.
            prefer != null -> FixTool.OBSERVE
            capabilities.peqVerified -> FixTool.PEQ
            capabilities.geqVerified && capabilities.fxRoutingVerified -> FixTool.GEQ
            else -> FixTool.OBSERVE
        }
        val cut = if (choice == FixTool.OBSERVE) 0.0 else minOf(maximumCutDb, if (score >= 90) 2.0 else 1.0)
        return FeedbackAdvice(choice, evidence.frequencyHz, -cut, score,
            if (choice == FixTool.OBSERVE) "No verified EQ capability; no changes permitted"
            else "Persistent narrowband peak; suggested $choice cut, requires operator confirmation and readback")
    }
}
/** Safety gate for a future OSC writer. No writes in v1.5. */
class EqWriteGuard(private val maxCumulativeCutDb: Double = 3.0) {
    fun permitted(originalDb: Double, proposedDb: Double, verified: Boolean, confirmed: Boolean): Boolean =
        verified && confirmed && proposedDb <= originalDb && proposedDb >= originalDb - maxCumulativeCutDb
}

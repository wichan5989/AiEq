package com.kai.soundgood.aigeq

/** Analyze whether a proposed cut reduced the observed narrowband peak. */
data class VerificationSample(val frequencyHz: Double, val peakDb: Double, val timestampMs: Long)
enum class VerificationStatus { IMPROVED, NO_CLEAR_IMPROVEMENT, INVALID }
data class VerificationResult(val status: VerificationStatus, val reductionDb: Double, val message: String)
class FeedbackVerifier {
    fun compare(before: List<VerificationSample>, after: List<VerificationSample>, targetHz: Double): VerificationResult {
        fun relevant(s: List<VerificationSample>) = s.filter {
            it.peakDb.isFinite() && it.frequencyHz > 0 && kotlin.math.abs(1200.0 * kotlin.math.log2(it.frequencyHz / targetHz)) < 100
        }.map { it.peakDb }.sorted()
        if (!targetHz.isFinite() || targetHz <= 0) return VerificationResult(VerificationStatus.INVALID,0.0,"Invalid frequency")
        val b=relevant(before); val a=relevant(after)
        if (b.size < 5 || a.size < 5) return VerificationResult(VerificationStatus.INVALID,0.0,"Need at least 5 comparable samples each")
        val reduction=b[b.size/2]-a[a.size/2]
        return VerificationResult(if (reduction >= 2.0) VerificationStatus.IMPROVED else VerificationStatus.NO_CLEAR_IMPROVEMENT,
            reduction, "Advisory only: microphone position, source and gain must remain unchanged")
    }
}

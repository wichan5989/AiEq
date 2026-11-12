package com.kai.soundgood.aigeq

/** Read-only diagnostic checks. Never writes EQ, gate, FX or routing. */
data class DiagnosticReport(
    val target: String,
    val received: Int,
    val expected: Int,
    val missing: List<String>,
    val malformedOrUnexpected: Int,
    val validNumericValues: Int,
    val status: String
)

object M32Diagnostics {
    fun summarize(snapshot: M32PeqSnapshot): DiagnosticReport {
        val numeric = snapshot.values.values.flatten().count { value ->
            when (value) {
                is Float -> value.isFinite()
                is Double -> value.isFinite()
                is Int -> true
                else -> false
            }
        }
        val expected = snapshot.values.size + snapshot.missing.size
        val status = when {
            expected == 0 -> "NO_QUERIES"
            snapshot.missing.isNotEmpty() -> "INCOMPLETE_READ_ONLY"
            snapshot.unexpected > 0 -> "COMPLETE_WITH_UNEXPECTED_PACKETS"
            else -> "COMPLETE_READ_ONLY"
        }
        return DiagnosticReport(snapshot.target.label, snapshot.values.size, expected,
            snapshot.missing, snapshot.unexpected, numeric, status)
    }
}

/** Fail-closed target validation before any network operation. */
object M32TargetValidation {
    fun valid(target: EqTarget): Boolean = when (target.kind) {
        TargetKind.CHANNEL -> target.number in 1..32
        TargetKind.BUS -> target.number in 1..16
        TargetKind.MATRIX -> target.number in 1..6
        TargetKind.MAIN_LR -> target.number == 1
    }
}

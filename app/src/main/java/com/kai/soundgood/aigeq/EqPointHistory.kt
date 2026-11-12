package com.kai.soundgood.aigeq

/** Per-band audit ledger for hardware undo preparation. No OSC write methods. */
enum class EqHistoryStatus { PROPOSED, PREVIEW_CONFIRMED, SIMULATED_APPLIED, SIMULATED_UNDONE, INVALIDATED }
data class EqHistoryEntry(
    val suggestionId: Long,
    val key: EqBandKey,
    val baselineDb: Double,
    val proposedDb: Double,
    val snapshotToken: String,
    val status: EqHistoryStatus,
    val revision: Long
)
sealed class EqHistoryResult {
    data class Ok(val entry: EqHistoryEntry): EqHistoryResult()
    data class Error(val reason: String): EqHistoryResult()
}

/** All state is in memory; application restart intentionally discards approvals. */
class EqPointHistory {
    private val entries = mutableMapOf<Long, EqHistoryEntry>()
    private val activeBand = mutableMapOf<EqBandKey, Long>()
    fun snapshot(): List<EqHistoryEntry> = entries.values.sortedBy { it.suggestionId }
    fun get(id: Long): EqHistoryEntry? = entries[id]
    fun clear() { entries.clear(); activeBand.clear() }

    fun propose(id: Long, key: EqBandKey, baselineDb: Double, proposedDb: Double,
                verifiedSnapshotToken: String): EqHistoryResult {
        if (id in entries) return EqHistoryResult.Error("Suggestion ID already recorded")
        if (key in activeBand) return EqHistoryResult.Error("Band already has a pending history entry")
        if (verifiedSnapshotToken.isBlank()) return EqHistoryResult.Error("Verified snapshot token required")
        if (!baselineDb.isFinite() || !proposedDb.isFinite() || proposedDb > baselineDb ||
            proposedDb < baselineDb - 3.0) return EqHistoryResult.Error("Invalid gain or cumulative cut")
        if (key.tool == EqKind.OFF || key.band !in 1..(if (key.tool == EqKind.GEQ) 31 else key.target.peqBands))
            return EqHistoryResult.Error("Invalid EQ band")
        val entry = EqHistoryEntry(id,key,baselineDb,proposedDb,verifiedSnapshotToken,EqHistoryStatus.PROPOSED,1)
        entries[id]=entry; activeBand[key]=id
        return EqHistoryResult.Ok(entry)
    }

    fun confirmPreview(id: Long, token: String, assistMode: Boolean): EqHistoryResult {
        val old=entries[id] ?: return EqHistoryResult.Error("Unknown suggestion")
        if (!assistMode || token != old.snapshotToken || old.status != EqHistoryStatus.PROPOSED)
            return EqHistoryResult.Error("Mode, snapshot or state mismatch")
        return change(old,EqHistoryStatus.PREVIEW_CONFIRMED)
    }

    /** Simulation only. Exact observed gain must match baseline; never talks to a mixer. */
    fun simulateApply(id: Long, observedDb: Double): EqHistoryResult {
        val old=entries[id] ?: return EqHistoryResult.Error("Unknown suggestion")
        if (old.status != EqHistoryStatus.PREVIEW_CONFIRMED || !observedDb.isFinite() ||
            kotlin.math.abs(observedDb-old.baselineDb)>0.05)
            return EqHistoryResult.Error("Stale baseline or not confirmed")
        return change(old,EqHistoryStatus.SIMULATED_APPLIED)
    }

    /** Hardware undo preparation: only the same band, only if current gain matches our proposal. */
    fun simulateUndo(id: Long, observedDb: Double): EqHistoryResult {
        val old=entries[id] ?: return EqHistoryResult.Error("Unknown suggestion")
        if (old.status != EqHistoryStatus.SIMULATED_APPLIED || !observedDb.isFinite() ||
            kotlin.math.abs(observedDb-old.proposedDb)>0.05)
            return EqHistoryResult.Error("Concurrent edit detected; undo blocked")
        return change(old,EqHistoryStatus.SIMULATED_UNDONE)
    }

    fun invalidate(id: Long): Boolean {
        val old=entries[id] ?: return false
        entries[id]=old.copy(status=EqHistoryStatus.INVALIDATED,revision=old.revision+1)
        activeBand.remove(old.key,id)
        return true
    }
    private fun change(old: EqHistoryEntry, status: EqHistoryStatus): EqHistoryResult {
        val next=old.copy(status=status,revision=old.revision+1)
        entries[old.suggestionId]=next
        if (status == EqHistoryStatus.SIMULATED_UNDONE) activeBand.remove(old.key,old.suggestionId)
        return EqHistoryResult.Ok(next)
    }
    fun canWriteHardware(): Boolean = false
}

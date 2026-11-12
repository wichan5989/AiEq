package com.kai.soundgood.aigeq

/** Preview-only queue. Undo reverses one preview, never a hardware EQ setting. */
enum class SuggestionStatus { PENDING, REVIEWED, CONFIRMED_PREVIEW, IGNORED }
data class FeedbackSuggestion(val id: Long, val target: String, val hz: Float, val cutDb: Float,
                              val status: SuggestionStatus = SuggestionStatus.PENDING)
class FeedbackSuggestionQueue {
    private val points = mutableListOf<FeedbackSuggestion>()
    var selectedId: Long? = null
        private set
    fun snapshot(): List<FeedbackSuggestion> = points.toList()
    fun clear() { points.clear(); selectedId = null }
    fun add(point: FeedbackSuggestion): Boolean {
        if (point.target.isBlank() || !point.hz.isFinite() || point.hz <= 0f ||
            !point.cutDb.isFinite() || point.cutDb >= 0f || point.cutDb < -3f ||
            points.any { it.id == point.id }) return false
        points.add(point.copy(status = SuggestionStatus.PENDING))
        if (selectedId == null) selectedId = point.id
        return true
    }
    fun current(): FeedbackSuggestion? = points.firstOrNull { it.id == selectedId }
    fun select(id: Long): Boolean {
        if (points.none { it.id == id }) return false
        selectedId = id
        return true
    }
    fun review(id: Long): Boolean = update(id, setOf(SuggestionStatus.PENDING), SuggestionStatus.REVIEWED)
    fun confirmPreview(id: Long, assistMode: Boolean): Boolean =
        assistMode && update(id, setOf(SuggestionStatus.REVIEWED), SuggestionStatus.CONFIRMED_PREVIEW)
    fun ignore(id: Long): Boolean = update(id, setOf(SuggestionStatus.PENDING, SuggestionStatus.REVIEWED), SuggestionStatus.IGNORED)
    fun undo(id: Long): Boolean = update(id,
        setOf(SuggestionStatus.REVIEWED, SuggestionStatus.CONFIRMED_PREVIEW, SuggestionStatus.IGNORED),
        SuggestionStatus.PENDING)
    fun next(): FeedbackSuggestion? {
        if (points.isEmpty()) return null
        val index = points.indexOfFirst { it.id == selectedId }.let { if (it < 0) 0 else it }
        selectedId = points[(index + 1) % points.size].id
        return current()
    }
    private fun update(id: Long, allowed: Set<SuggestionStatus>, next: SuggestionStatus): Boolean {
        val i = points.indexOfFirst { it.id == id }
        if (i < 0 || points[i].status !in allowed) return false
        points[i] = points[i].copy(status = next)
        return true
    }
    /** Hardware writes deliberately unavailable in all modes. */
    fun mayWriteToMixer(): Boolean = false
}

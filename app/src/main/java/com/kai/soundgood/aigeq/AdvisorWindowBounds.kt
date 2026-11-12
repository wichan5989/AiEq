package com.kai.soundgood.aigeq

/** Screen-space clamp used by floating UI layout tests. */
object AdvisorWindowBounds {
 fun clamp(value: Float, viewport: Float, window: Float): Float {
  require(value.isFinite() && viewport.isFinite() && window.isFinite())
  require(viewport >= 0f && window >= 0f)
  return value.coerceIn(0f, (viewport-window).coerceAtLeast(0f))
 }
}

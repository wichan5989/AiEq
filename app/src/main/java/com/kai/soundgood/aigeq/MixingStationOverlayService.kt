package com.kai.soundgood.aigeq

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Switch
import android.view.View

/**
 * Independent overlay prototype. Does not capture audio or write mixer EQ.
 * Launch only from a visible Activity after Settings.canDrawOverlays(context).
 */
class MixingStationOverlayService : Service() {
    private var manager: WindowManager? = null
    private var panel: View? = null
    private val gate = AutoFeedbackSafetyGate()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT < 26 || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        manager = getSystemService(WINDOW_SERVICE) as WindowManager
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 14, 22, 14)
            setBackgroundColor(Color.rgb(23, 27, 34))
        }
        fun line(value: String, size: Float): TextView = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(line("KAI · FEEDBACK MONITOR", 14f))
        root.addView(line("No audio feed · MONITOR ONLY", 12f))
        val toggle = Switch(this).apply {
            text = "Auto Feedback (OFF)"
            isChecked = false
            setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, checked ->
                if (checked) gate.enableAuto() else gate.disable()
                // Never arm automatically: UI is a prototype and has no live mixer verification.
                text = if (checked) "Auto requested · DISARMED" else "Auto Feedback (OFF)"
            }
        }
        root.addView(toggle)
        val close = line("CLOSE  ×", 12f).apply {
            gravity = Gravity.END
            setPadding(0, 10, 0, 0)
            setOnClickListener { stopSelf() }
        }
        root.addView(close)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 12
            y = 90
        }
        try {
            manager?.addView(root, params)
            panel = root
        } catch (_: Exception) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        panel?.let { try { manager?.removeView(it) } catch (_: Exception) {} }
        panel = null
        gate.disable()
        super.onDestroy()
    }
}

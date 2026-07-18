package com.ezyapp.wattflow

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs

object OverlayPrefs {
    private fun prefs(c: Context) =
        c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun enabled(c: Context) = prefs(c).getBoolean("overlay_enabled", false)
    fun setEnabled(c: Context, on: Boolean) =
        prefs(c).edit().putBoolean("overlay_enabled", on).apply()

    fun x(c: Context) = prefs(c).getInt("overlay_x", 0)
    fun y(c: Context) = prefs(c).getInt("overlay_y", 200)
    fun setPos(c: Context, x: Int, y: Int) =
        prefs(c).edit().putInt("overlay_x", x).putInt("overlay_y", y).apply()

    fun canDraw(c: Context) = Settings.canDrawOverlays(c)

    fun permissionIntent(c: Context) = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${c.packageName}"),
    )
}

/**
 * Draggable watts pill drawn over other apps. Driven by the monitor
 * service's sampling loop; all view work is posted to the main thread.
 */
class OverlayController(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val wm =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    fun update(sample: BatterySample) {
        handler.post {
            if (!OverlayPrefs.enabled(context) || !OverlayPrefs.canDraw(context)) {
                removeInternal()
                return@post
            }
            val v = view ?: attach() ?: return@post
            v.text = String.format(
                Locale.US,
                if (sample.isCharging) "▲ %.1f W" else "▼ %.1f W",
                abs(sample.watts),
            )
        }
    }

    fun hide() {
        handler.post { removeInternal() }
    }

    private fun removeInternal() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        params = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attach(): TextView? {
        val v = TextView(context).apply {
            setBackgroundResource(R.drawable.overlay_pill_bg)
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            val padH = (12 * resources.displayMetrics.density).toInt()
            val padV = (6 * resources.displayMetrics.density).toInt()
            setPadding(padH, padV, padH, padV)
        }
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = OverlayPrefs.x(context)
            y = OverlayPrefs.y(context)
        }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragged = false
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = p.x; startY = p.y
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (dragged || abs(dx) > 12 || abs(dy) > 12) {
                        dragged = true
                        p.x = startX + dx.toInt()
                        p.y = startY + dy.toInt()
                        runCatching { wm.updateViewLayout(v, p) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragged) {
                        OverlayPrefs.setPos(context, p.x, p.y)
                    } else {
                        context.startActivity(
                            Intent(context, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    true
                }
                else -> false
            }
        }

        return try {
            wm.addView(v, p)
            view = v
            params = p
            v
        } catch (_: Exception) {
            // Permission revoked between check and add, or bad token.
            null
        }
    }
}

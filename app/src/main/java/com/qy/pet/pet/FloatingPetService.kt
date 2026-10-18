package com.qy.pet.pet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.qy.pet.R
import kotlin.math.abs
import kotlin.random.Random

class FloatingPetService : Service() {

    private lateinit var wm: WindowManager
    private var container: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundNotify()
        addPet()
        PetState.running = true
    }

    private fun startForegroundNotify() {
        val chId = "qy_pet"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(chId, "奶蛙桌宠", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
        val n: Notification = NotificationCompat.Builder(this, chId)
            .setContentTitle("奶蛙运行中")
            .setSmallIcon(R.drawable.ic_launcher)
            .build()
        startForeground(101, n)
    }

    private fun addPet() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0; y = 300
        }
        layoutParams = lp

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        PetState.scale = prefs.getFloat("scale", 0.35f)

        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bubble = TextView(this).apply {
            text = PetState.idleLines.random()
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            visibility = if (PetState.showBubble) TextView.VISIBLE else TextView.GONE
            setPadding(16, 8, 16, 8)
        }
        val pet = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher)
            val size = (220 * PetState.scale * 3f).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
        box.addView(bubble)
        box.addView(pet)
        container = box

        attachDrag(box, lp, bubble)
        wm.addView(box, lp)
    }

    private fun attachDrag(
        view: LinearLayout,
        lp: WindowManager.LayoutParams,
        bubble: TextView,
    ) {
        var downX = 0f; var downY = 0f; var moved = false
        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(e.rawX - downX) + abs(e.rawY - downY) > 12) moved = true
                    lp.x = (e.rawX - downX).toInt(); lp.y = (e.rawY - downY).toInt()
                    wm.updateViewLayout(view, lp)
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved && PetState.clickReaction) {
                        bubble.text = PetState.pettedLines.random()
                        bubble.visibility = TextView.VISIBLE
                    }
                }
            }
            true
        }
    }

    override fun onDestroy() {
        PetState.running = false
        container?.let { runCatching { wm.removeView(it) } }
        super.onDestroy()
    }
}

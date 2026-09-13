package com.spellinggate.app.gate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.spellinggate.app.MainActivity

class PersonalGateService : Service() {
    private lateinit var windowManager: WindowManager
    private var overlay: LinearLayout? = null

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) {
                runCatching { GateSessionStore(context).prepareNewGate() }
                    .onSuccess { guard() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle("Spelling Gate is active")
                .setContentText("Watching for phone unlocks")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build(),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT), RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_GUARD -> guard()
            ACTION_VISIBLE -> removeOverlay()
            ACTION_RELEASE -> removeOverlay()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        removeOverlay()
        runCatching { unregisterReceiver(unlockReceiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun guard() {
        if (!Settings.canDrawOverlays(this) || !GateSessionStore(this).isLocked()) return
        showOverlay()
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(activityIntent)
    }

    private fun showOverlay() {
        if (overlay != null) return
        val density = resources.displayMetrics.density
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((24 * density).toInt(), 0, (24 * density).toInt(), 0)
            setBackgroundColor(Color.rgb(16, 27, 24))
            addView(TextView(context).apply {
                text = "SPELLING GATE\nReturn to the challenge to continue"
                textSize = 24f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
            })
            addView(Button(context).apply {
                text = "Return to Challenge"
                backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(131, 217, 189))
                setTextColor(Color.rgb(13, 57, 47))
                setOnClickListener { guard() }
            })
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        )
        windowManager.addView(layout, params)
        overlay = layout
    }

    private fun removeOverlay() {
        overlay?.let { runCatching { windowManager.removeView(it) } }
        overlay = null
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Spelling Gate monitoring", NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        private const val CHANNEL_ID = "spelling_gate_monitor"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_GUARD = "com.spellinggate.app.GUARD"
        private const val ACTION_VISIBLE = "com.spellinggate.app.VISIBLE"
        private const val ACTION_RELEASE = "com.spellinggate.app.RELEASE"
        private const val PREFS = "personal_gate"
        private const val KEY_ENABLED = "enabled"

        fun start(context: Context) {
            context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, true).apply()
            context.startForegroundService(Intent(context, PersonalGateService::class.java))
        }
        fun guard(context: Context) = send(context, ACTION_GUARD)
        fun activityVisible(context: Context) = send(context, ACTION_VISIBLE)
        fun release(context: Context) = send(context, ACTION_RELEASE)
        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ENABLED, false)
        private fun send(context: Context, action: String) {
            context.startService(Intent(context, PersonalGateService::class.java).setAction(action))
        }
    }
}

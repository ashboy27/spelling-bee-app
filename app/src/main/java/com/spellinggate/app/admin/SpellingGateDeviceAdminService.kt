package com.spellinggate.app.admin

import android.app.ActivityOptions
import android.app.admin.DeviceAdminService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.spellinggate.app.MainActivity
import com.spellinggate.app.gate.DevicePolicyController
import com.spellinggate.app.gate.GateSessionStore

class SpellingGateDeviceAdminService : DeviceAdminService() {
    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_USER_PRESENT) return
            prepareAndLaunchGate(context)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(userPresentReceiver, filter, RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(userPresentReceiver, filter)
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(userPresentReceiver) }
        super.onDestroy()
    }

    private fun prepareAndLaunchGate(context: Context) {
        val policy = DevicePolicyController(context)
        if (!policy.isDeviceOwner) return

        val prepared = runCatching {
            GateSessionStore(context).prepareNewGate()
            policy.configureLockTask()
        }.onFailure { Log.e(TAG, "Unlock gate could not be prepared", it) }
            .getOrDefault(false)
        if (!prepared) return

        val gateIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_NEW_UNLOCK_EVENT, true)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && policy.isLockTaskPermitted()) {
                val options = ActivityOptions.makeBasic()
                    .setLockTaskEnabled(true)
                    .toBundle()
                context.startActivity(gateIntent, options)
            } else {
                context.startActivity(gateIntent)
            }
        }.onFailure { Log.e(TAG, "Unlock gate could not be shown", it) }
    }

    private companion object {
        const val TAG = "SpellingGateAdmin"
    }
}

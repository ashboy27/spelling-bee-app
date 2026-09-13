package com.spellinggate.app.gate

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.spellinggate.app.admin.SpellingGateDeviceAdminReceiver

class DevicePolicyController(context: Context) {
    private val appContext = context.applicationContext
    private val policyManager = appContext.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(appContext, SpellingGateDeviceAdminReceiver::class.java)

    val isDeviceOwner: Boolean
        get() = policyManager.isDeviceOwnerApp(appContext.packageName)

    fun configureLockTask(): Boolean {
        if (!isDeviceOwner) return false

        return runCatching {
            policyManager.setLockTaskPackages(admin, arrayOf(appContext.packageName))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                policyManager.setLockTaskFeatures(
                    admin,
                    DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS,
                )
            }
            true
        }.onFailure { Log.e(TAG, "Could not configure lock task", it) }
            .getOrDefault(false)
    }

    fun isLockTaskPermitted(): Boolean =
        isDeviceOwner && policyManager.isLockTaskPermitted(appContext.packageName)

    fun enterLockTask(activity: Activity): Boolean {
        if (!configureLockTask() || !isLockTaskPermitted()) return false
        if (lockTaskMode() == ActivityManager.LOCK_TASK_MODE_NONE) {
            activity.startLockTask()
        }
        return lockTaskMode() == ActivityManager.LOCK_TASK_MODE_LOCKED
    }

    fun releaseLockTask(activity: Activity) {
        if (lockTaskMode() != ActivityManager.LOCK_TASK_MODE_NONE) {
            runCatching { activity.stopLockTask() }
                .onFailure { Log.e(TAG, "Could not stop lock task", it) }
        }
    }

    private fun lockTaskMode(): Int =
        appContext.getSystemService(ActivityManager::class.java).lockTaskModeState

    private companion object {
        const val TAG = "SpellingGatePolicy"
    }
}

package com.spellinggate.app.admin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.spellinggate.app.gate.DevicePolicyController
import com.spellinggate.app.gate.PersonalGateService

class SpellingGateBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        runCatching { DevicePolicyController(context).configureLockTask() }
            .onFailure { Log.e(TAG, "Lock task policy was not restored", it) }
        if (PersonalGateService.isEnabled(context)) {
            runCatching { PersonalGateService.start(context) }
                .onFailure { Log.e(TAG, "Personal gate monitor was not restarted", it) }
        }
    }

    private companion object {
        const val TAG = "SpellingGateBoot"
    }
}

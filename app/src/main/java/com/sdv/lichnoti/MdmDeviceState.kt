package com.sdv.lichnoti

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager

object MdmDeviceState {
    fun isUnlockedAndInteractive(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return powerManager.isInteractive &&
            !keyguardManager.isKeyguardLocked &&
            !keyguardManager.isDeviceLocked
    }
}

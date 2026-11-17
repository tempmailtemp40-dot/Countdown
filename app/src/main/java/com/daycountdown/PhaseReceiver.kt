package com.daycountdown

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Runs at 7 am and 12 am, and after reboot, app update or clock changes. */
class PhaseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CountdownScheduler.apply(context)
    }
}

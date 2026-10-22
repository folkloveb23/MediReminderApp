package com.example.medireminderapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.auth.FirebaseAuth

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                Log.d("BOOT_RX", "Device booted; reschedule alarms for today")
                NotificationHelper.ensureChannel(context) // เผื่อ channel หาย
                AlarmScheduler.scheduleToday(context)
            } else {
                Log.d("BOOT_RX", "No user logged in; skip reschedule")
            }
        }
    }
}

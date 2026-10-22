package com.example.medireminderapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth

class MidnightRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // ถ้ายังล็อกอินอยู่ → ตั้งปลุกของ "วันนี้"
        if (FirebaseAuth.getInstance().currentUser != null) {
            AlarmScheduler.scheduleToday(context)
        }
        // ตั้งตัวเองสำหรับคืนถัดไปอีกครั้ง
        AlarmScheduler.scheduleMidnightRefresh(context)
    }
}

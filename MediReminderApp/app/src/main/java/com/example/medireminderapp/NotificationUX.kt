package com.example.medireminderapp

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * แบนเนอร์แจ้งเตือนผู้ใช้ให้ "อนุญาตแจ้งเตือน", "เปิด Notification Channel", และ "Exact Alarm"
 * แสดงเมื่อเงื่อนไขยังไม่พร้อม และมีปุ่มเปิด Settings / ขอสิทธิ ให้กดได้ทันที
 *
 * ใช้ในจุดบนสุดของ UI (ก่อน NavHost) เช่น:
 *   Column { NotificationsBanner(); NavHost(...) }
 */
@Composable
fun NotificationsBanner() {
    val ctx = LocalContext.current
    val nm = remember { ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    val am = remember { ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager }

    // ตรวจสถานะต่าง ๆ แบบ state ที่รีเฟรชได้
    var notifEnabled by remember { mutableStateOf(nm.areNotificationsEnabled()) }
    var channelEnabled by remember {
        mutableStateOf(isChannelEnabled(nm, NotificationHelper.CHANNEL_ID))
    }
    var exactAllowed by remember {
        mutableStateOf(if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true)
    }
    var postGranted by remember {
        mutableStateOf(isPostNotifGranted(ctx))
    }

    // ถ้าทุกอย่างพร้อมแล้ว ไม่ต้องแสดงอะไร
    if (notifEnabled && channelEnabled && exactAllowed && postGranted) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        ElevatedCard(colors = CardDefaults.elevatedCardColors()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

                Text("ตั้งค่าการแจ้งเตือนให้พร้อมใช้งาน", style = MaterialTheme.typography.titleMedium)

                if (!notifEnabled) {
                    Text("• ระบบแจ้งเตือนของแอปถูกปิดอยู่", color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            openAppNotificationSettings(ctx)
                        }) { Text("เปิดการแจ้งเตือน") }
                        OutlinedButton(onClick = { /* no-op */ }) {
                            Text("คำอธิบาย: ไปที่การตั้งค่า > การแจ้งเตือน > อนุญาต")
                        }
                    }
                }

                if (notifEnabled && !channelEnabled) {
                    Text("• ช่องแจ้งเตือน (Medicine Reminders) ถูกปิดอยู่", color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { openChannelSettings(ctx, NotificationHelper.CHANNEL_ID) }) {
                            Text("เปิดช่องแจ้งเตือน")
                        }
                        OutlinedButton(onClick = {}) { Text("แก้ไขความสำคัญ/เสียง") }
                    }
                }

                if (!postGranted && Build.VERSION.SDK_INT >= 33) {
                    Text("• ยังไม่ได้อนุญาตสิทธิ POST_NOTIFICATIONS (Android 13+)", color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { requestPostNotifications(ctx) }) { Text("ขอสิทธิ") }
                        OutlinedButton(onClick = {}) { Text("จำเป็นสำหรับแสดงแจ้งเตือน") }
                    }
                }

                if (!exactAllowed && Build.VERSION.SDK_INT >= 31) {
                    Text("• ยังไม่ได้อนุญาต Exact Alarm (Android 12+)", color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { requestExactAlarm(ctx) }) { Text("อนุญาต Exact Alarm") }
                        OutlinedButton(onClick = {}) { Text("ใช้ปลุกตรงเวลายา") }
                    }
                }

                // แถบรีเฟรชสถานะหลังผู้ใช้กลับมาจาก Settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = {
                        notifEnabled = nm.areNotificationsEnabled()
                        channelEnabled = isChannelEnabled(nm, NotificationHelper.CHANNEL_ID)
                        exactAllowed = if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
                        postGranted = isPostNotifGranted(ctx)
                    }) { Text("ตรวจใหม่") }

                    Spacer(Modifier.weight(1f))

                    // ปุ่มเทสแจ้งเตือนแบบ one-shot
                    //OutlinedButton(onClick = { TestAlarmHelper.fireIn15s(ctx) }) { Text("ทดสอบเด้งใน 15 วิ") }
                }
            }
        }
    }
}

/* ---------------- Utils & helpers ---------------- */

private fun isPostNotifGranted(ctx: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= 33)
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    else true
}

private fun requestPostNotifications(ctx: Context) {
    if (Build.VERSION.SDK_INT >= 33) {
        val act = ctx.findActivity() ?: return
        ActivityCompat.requestPermissions(
            act,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            9001
        )
    }
}

private fun isChannelEnabled(nm: NotificationManager, channelId: String): Boolean {
    return if (Build.VERSION.SDK_INT >= 26) {
        val ch = nm.getNotificationChannel(channelId) ?: return false
        ch.importance != NotificationManager.IMPORTANCE_NONE
    } else true
}

private fun openAppNotificationSettings(ctx: Context) {
    val intent = Intent().apply {
        action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
        putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
    }
    ctx.startActivity(intent)
}

private fun openChannelSettings(ctx: Context, channelId: String) {
    if (Build.VERSION.SDK_INT >= 26) {
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
        }
        ctx.startActivity(intent)
    } else {
        openAppNotificationSettings(ctx)
    }
}

private fun requestExactAlarm(ctx: Context) {
    if (Build.VERSION.SDK_INT >= 31) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
        } catch (_: Exception) {
            // บางรอมอาจไม่มีหน้า settings นี้
        }
    }
}

/** หา Activity จาก Context ปัจจุบัน */
private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

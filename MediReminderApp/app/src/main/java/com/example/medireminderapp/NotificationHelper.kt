package com.example.medireminderapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {

    const val CHANNEL_ID = "medireminder.reminders"
    private const val CHANNEL_NAME = "เตือนกินยา"
    private const val CHANNEL_DESC = "แจ้งเตือนเวลาทานยา/ใช้ยา พร้อมปุ่ม ทานแล้ว/ข้าม"

    /** เรียกครั้งเดียวตอนแอพเริ่ม (ทำแล้วใน MainActivity) */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    /** เปิดแอพเมื่อแตะการ์ดแจ้งเตือน */
    private fun appPendingIntent(context: Context): PendingIntent {
        val i = context.packageManager.getLaunchIntentForPackage(context.packageName)!!.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context, 1000, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** สร้าง Notification “ทรงเดียวกับภาพ” */
    fun buildReminder(
        context: Context,
        title: String = "เตือนกินยา",
        nowText: String,          // "ขณะนี้เวลา 21:03"
        nameText: String,         // "ได้เวลาทานยา/ใช้ยา Dobutamine"
        qtyText: String,          // "จำนวน 1 เม็ด"
        actionTaken: PendingIntent,
        actionSkip: PendingIntent
    ): Notification {
        // เนื้อหา 3 บรรทัดเหมือนภาพ
        val big = "$nowText\n$nameText\n$qtyText"

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // เปลี่ยนเป็นไอคอนยาได้
            .setContentTitle(title)
            .setContentText("$nowText • $nameText")
            .setStyle(NotificationCompat.BigTextStyle().bigText(big))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(appPendingIntent(context))
            // ปุ่มที่ด้านล่างการ์ด
            .addAction(0, "ทานแล้ว", actionTaken)
            .addAction(0, "ข้าม", actionSkip)
            .build()
    }
}

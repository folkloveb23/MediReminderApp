package com.example.medireminderapp

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * รับสัญญาณจาก AlarmManager แล้วแสดง Notification “เตือนกินยา”
 * มีปุ่ม: ทานแล้ว (ACTION_TAKEN), ข้าม (ACTION_SKIP)
 * ถ้าไม่ตอบ จะสโน๊ซ (+10 นาที) 1 รอบ
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ALARM_RX"

        // Extras
        const val EXTRA_MED_ID   = "medId"
        const val EXTRA_MED_NAME = "medName"
        const val EXTRA_TIME     = "timeText"    // "21:03"
        const val EXTRA_QTY_TEXT = "qtyText"     // "จำนวน 1 เม็ด"
        const val EXTRA_EVENT_ID = "eventId"
        const val EXTRA_ATTEMPT  = "attempt"     // สโน๊ซรอบที่ n

        // Actions
        const val ACTION_TAKEN = "MEDI.ACTION_TAKEN"
        const val ACTION_SKIP  = "MEDI.ACTION_SKIP"
    }

    override fun onReceive(context: Context, intent: Intent) {
        NotificationHelper.ensureChannel(context)

        val action  = intent.action
        val medId   = intent.getStringExtra(EXTRA_MED_ID) ?: ""
        val medName = intent.getStringExtra(EXTRA_MED_NAME) ?: "ยา"
        val time    = intent.getStringExtra(EXTRA_TIME) ?: "--:--"
        val qtyText = intent.getStringExtra(EXTRA_QTY_TEXT) ?: ""
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: UUID.randomUUID().toString()
        val attempt = intent.getIntExtra(EXTRA_ATTEMPT, 0)

        when (action) {
            ACTION_TAKEN -> {
                Log.d(TAG, "ACTION_TAKEN: $medId $medName")
                markTaken(context, medId)
                cancelNotification(context, eventId.hashCode())
                return
            }
            ACTION_SKIP -> {
                Log.d(TAG, "ACTION_SKIP: $medId $medName")
                markSkipped(context, medId)
                cancelNotification(context, eventId.hashCode())
                return
            }
        }

        // สร้าง PendingIntent สำหรับปุ่ม
        val takenPi = PendingIntent.getBroadcast(
            context,
            Random.nextInt(),
            Intent(context, AlarmReceiver::class.java).apply {
                // แก้จุดพัง: ห้ามเขียน action = ... ให้ใช้ setAction / this.action
                setAction(ACTION_TAKEN)
                putExtra(EXTRA_MED_ID, medId)
                putExtra(EXTRA_MED_NAME, medName)
                putExtra(EXTRA_TIME, time)
                putExtra(EXTRA_QTY_TEXT, qtyText)
                putExtra(EXTRA_EVENT_ID, eventId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val skipPi = PendingIntent.getBroadcast(
            context,
            Random.nextInt(),
            Intent(context, AlarmReceiver::class.java).apply {
                setAction(ACTION_SKIP)
                putExtra(EXTRA_MED_ID, medId)
                putExtra(EXTRA_MED_NAME, medName)
                putExtra(EXTRA_TIME, time)
                putExtra(EXTRA_QTY_TEXT, qtyText)
                putExtra(EXTRA_EVENT_ID, eventId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // สร้างการ์ดแจ้งเตือน “ทรงเดียวกับภาพ”
        val n = NotificationHelper.buildReminder(
            context = context,
            nowText = "ขณะนี้เวลา $time",
            nameText = "ได้เวลาทานยา/ใช้ยา $medName",
            qtyText = qtyText.ifBlank { "จำนวน -" },
            actionTaken = takenPi,
            actionSkip = skipPi
        )

        NotificationManagerCompat.from(context).notify(eventId.hashCode(), n)

        // สโน๊ซ +10 นาที ถ้ายังไม่มีการกดปุ่ม (ทำ 1 รอบ)
        if (attempt < 1) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(
                context,
                eventId.hashCode() + attempt + 1,
                Intent(context, AlarmReceiver::class.java).apply {
                    // ไม่ทับ action เดิมในบอดี้หลัก
                    putExtra(EXTRA_MED_ID, medId)
                    putExtra(EXTRA_MED_NAME, medName)
                    putExtra(EXTRA_TIME, time)
                    putExtra(EXTRA_QTY_TEXT, qtyText)
                    putExtra(EXTRA_EVENT_ID, eventId)
                    putExtra(EXTRA_ATTEMPT, attempt + 1)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10),
                pi
            )
        }
    }

    private fun cancelNotification(context: Context, id: Int) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id)
    }

    /** ลดสต็อก + บันทึกประวัติแบบย่อ (คุณปรับลอจิกเพิ่มได้) */
    private fun markTaken(context: Context, medId: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db  = FirebaseFirestore.getInstance()

        db.collection("users").document(uid)
            .collection("medicines").document(medId)
            .update("quantityRemaining", FieldValue.increment(-1))

        val key = System.currentTimeMillis().toString()
        db.collection("users").document(uid)
            .collection("history").document(key)
            .set(
                mapOf(
                    "medicineId" to medId,
                    "status" to "taken",
                    "timestamp" to System.currentTimeMillis()
                )
            )
    }

    private fun markSkipped(context: Context, medId: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db  = FirebaseFirestore.getInstance()
        val key = System.currentTimeMillis().toString()
        db.collection("users").document(uid)
            .collection("history").document(key)
            .set(
                mapOf(
                    "medicineId" to medId,
                    "status" to "skipped",
                    "timestamp" to System.currentTimeMillis()
                )
            )
    }
}

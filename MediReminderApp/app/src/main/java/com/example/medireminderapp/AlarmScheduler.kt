package com.example.medireminderapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * ตั้งปลุกจาก "events ของวันนี้" ใน Firestore:
 * users/{uid}/events/{yyyyMMdd}/{yyyyMMdd}/
 *
 * เรียกใช้เมื่อ:
 *  - หลังล็อกอินสำเร็จ
 *  - หลังเพิ่ม/แก้ไขยา (หลัง upsertEventsFromToday)
 *  - เมื่อแอพกลับมา foreground (onResume)
 *  - ตอนเที่ยงคืน (ผ่าน scheduleMidnightRefresh + MidnightRefreshReceiver)
 *  - หลังรีบูตเครื่อง (BootReceiver)
 */
object AlarmScheduler {

    private const val TAG = "ALARM_SCH"

    /** คืนค่าคีย์วันปัจจุบันรูปแบบ yyyyMMdd */
    private fun todayKey(cal: Calendar = Calendar.getInstance()): String {
        cal.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.time)
    }

    /** ✅ ตั้งปลุกทั้งหมดของ "วันนี้" จากเอกสาร events */
    fun scheduleToday(context: Context, onDone: (() -> Unit)? = null) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Log.w(TAG, "skip scheduleToday: no user")
            return
        }
        val db = FirebaseFirestore.getInstance()
        val key = todayKey()

        db.collection("users").document(uid)
            .collection("events").document(key).collection(key)
            .get()
            .addOnSuccessListener { qs ->
                Log.d(TAG, "events for $key = ${qs.size()}")

                // เคลียร์ก่อน กันซ้อน
                cancelAllToday(context)

                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

                for (d in qs.documents) {
                    val name = d.getString("name") ?: "ยา"
                    val time = d.getString("time") ?: "--:--"
                    val ts = (d.getLong("timestamp") ?: 0L)
                        .coerceAtLeast(System.currentTimeMillis() + 2_000)
                    val medId = d.getString("medicineId")

                    // ใช้ if-guard แทน continue
                    if (medId.isNullOrBlank()) {
                        Log.w(TAG, "skip event=${d.id} because medicineId is null/blank")
                    } else {
                        val qtyText = "" // เติม "จำนวน X เม็ด" ได้ถ้ามีข้อมูล

                        val intent = Intent(context, AlarmReceiver::class.java).apply {
                            putExtra(AlarmReceiver.EXTRA_MED_ID, medId)
                            putExtra(AlarmReceiver.EXTRA_MED_NAME, name)
                            putExtra(AlarmReceiver.EXTRA_TIME, time)
                            putExtra(AlarmReceiver.EXTRA_QTY_TEXT, qtyText)
                            putExtra(AlarmReceiver.EXTRA_EVENT_ID, d.id)
                            putExtra(AlarmReceiver.EXTRA_ATTEMPT, 0)
                        }

                        val pi = PendingIntent.getBroadcast(
                            context,
                            d.id.hashCode(),
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        Log.d(TAG, "setExact: event=${d.id} at=${Date(ts)} name=$name time=$time")
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ts, pi)
                    }
                }

                onDone?.invoke()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "load events failed", e)
                onDone?.invoke()
            }
    }

    /** (ออปชันแนะนำ) ตั้งปลุกรีเฟรชอัตโนมัติ 00:01 ของวันถัดไป */
    fun scheduleMidnightRefresh(context: Context) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 1) // 00:01
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_MONTH, 1)
        }
        val intent = Intent(context, MidnightRefreshReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 424242, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        Log.d(TAG, "scheduleMidnightRefresh at=${Date(cal.timeInMillis)}")
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
    }

    /** (เครื่องมือดีบัก) ตั้งปลุกให้เด้งในอีก N วินาที */
    fun scheduleDebugAfter(context: Context, seconds: Long = 15L) {
        val eventId = "debug_${System.currentTimeMillis()}"
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_MED_ID, "debug_med")
            putExtra(AlarmReceiver.EXTRA_MED_NAME, "DEBUG PILL")
            putExtra(AlarmReceiver.EXTRA_TIME, "NOW")
            putExtra(AlarmReceiver.EXTRA_QTY_TEXT, "จำนวน 1 เม็ด")
            putExtra(AlarmReceiver.EXTRA_EVENT_ID, eventId)
            putExtra(AlarmReceiver.EXTRA_ATTEMPT, 0)
        }
        val pi = PendingIntent.getBroadcast(
            context, eventId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val whenTs = System.currentTimeMillis() + seconds * 1000
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        Log.d(TAG, "scheduleDebugAfter: +${seconds}s at=${Date(whenTs)}")
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenTs, pi)
    }

    /** จุดยกเลิก (ถ้าต้องการเก็บ requestCode ไว้แล้ววน cancel เอง) */
    fun cancelAllToday(context: Context) {
        // ปล่อยให้ระบบ override ด้วย requestCode เดิม
        // ถ้าต้องการ cancel จริง ๆ ให้เก็บรายการ requestCode ที่ตั้งไป แล้ววนยกเลิกผ่าน AlarmManager
    }
}

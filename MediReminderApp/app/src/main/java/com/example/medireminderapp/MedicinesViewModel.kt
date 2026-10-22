package com.example.medireminderapp

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

private const val TAG = "MedicinesVM"

/* -------------------- Model -------------------- */

data class Medicine(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val dosage: String = "",
    val quantityRemaining: Int = 0,
    val frequency: String = "",
    val daysOfWeek: List<String> = emptyList(),
    val times: List<String> = emptyList(),
    val isCourse: Boolean = false,
    val endDate: Long? = null,
    val courseStart: Long? = null,        // จุดเริ่มคอร์ส (ถ้าแก้จำนวน ให้เลื่อนไปเป็นวันนี้)
    val isRefillReminder: Boolean = true,
    val refillThreshold: Int = 0,
    val intervalDays: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
) {
    companion object {
        fun fromDoc(doc: DocumentSnapshot): Medicine? {
            val data = doc.data ?: return null

            fun anyInt(k: String, def: Int = 0) = when (val v = data[k]) {
                is Number -> v.toInt()
                is String -> v.toIntOrNull() ?: def
                else -> def
            }
            fun anyLong(k: String, def: Long = 0L) = when (val v = data[k]) {
                is Number -> v.toLong()
                is Timestamp -> v.toDate().time
                is String -> v.toLongOrNull() ?: def
                else -> def
            }
            fun anyString(k: String, def: String = "") = (data[k] as? String) ?: def
            fun anyBool(k: String, def: Boolean = false) = (data[k] as? Boolean) ?: def
            fun anyStrList(k: String) = (data[k] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

            return try {
                Medicine(
                    id = doc.id,
                    userId = anyString("userId"),
                    name = anyString("name"),
                    dosage = anyString("dosage"),
                    quantityRemaining = anyInt("quantityRemaining"),
                    frequency = anyString("frequency"),
                    daysOfWeek = anyStrList("daysOfWeek"),
                    times = anyStrList("times"),
                    isCourse = anyBool("isCourse"),
                    endDate = when (val v = data["endDate"]) {
                        is Number -> v.toLong()
                        is Timestamp -> v.toDate().time
                        else -> null
                    },
                    courseStart = when (val v = data["courseStart"]) {
                        is Number -> v.toLong()
                        is Timestamp -> v.toDate().time
                        else -> null
                    },
                    isRefillReminder = anyBool("isRefillReminder", true),
                    refillThreshold = anyInt("refillThreshold"),
                    intervalDays = anyInt("intervalDays"),
                    createdAt = anyLong("createdAt", System.currentTimeMillis()),
                    isDeleted = anyBool("isDeleted")
                )
            } catch (e: Exception) {
                Log.e(TAG, "fromDoc parse error ${doc.id}", e)
                null
            }
        }
    }
}

/* -------------------- ViewModel -------------------- */

class MedicinesViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private var reg: ListenerRegistration? = null
    private var takenReg: ListenerRegistration? = null
    private var cancelledReg: ListenerRegistration? = null

    private val _items = MutableStateFlow<List<Medicine>>(emptyList())
    val items: StateFlow<List<Medicine>> = _items

    private val _takenForDate = MutableStateFlow<Set<String>>(emptySet())
    val takenForDate: StateFlow<Set<String>> = _takenForDate

    private val _cancelledForDate = MutableStateFlow<Set<String>>(emptySet())
    val cancelledForDate: StateFlow<Set<String>> = _cancelledForDate

    private fun uidOrNull() = FirebaseAuth.getInstance().currentUser?.uid
    private fun medsPath(uid: String) = db.collection("users").document(uid).collection("medicines")

    /* ------------ Live medicines ------------ */

    fun listenMedicines() {
        val uid = uidOrNull() ?: return
        reg?.remove()
        reg = medsPath(uid)
            .whereEqualTo("isDeleted", false)
            .orderBy("createdAt")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "listenMedicines error", err); return@addSnapshotListener
                }
                _items.value = snap?.documents?.mapNotNull { Medicine.fromDoc(it) }.orEmpty()
            }
    }

    fun refreshOnce() {
        val uid = uidOrNull() ?: return
        medsPath(uid).whereEqualTo("isDeleted", false).orderBy("createdAt")
            .get()
            .addOnSuccessListener { qs -> _items.value = qs.documents.mapNotNull { Medicine.fromDoc(it) } }
            .addOnFailureListener { Log.e(TAG, "refreshOnce failed", it) }
    }

    /* ------------ Add / Update ------------ */

    fun addMedicine(
        name: String,
        dosage: String,
        quantityRemaining: Int,
        frequency: String,
        daysOfWeek: List<String>,
        times: List<String>,
        isCourse: Boolean,
        endDate: Long?,
        isRefillReminder: Boolean,
        refillThreshold: Int,
        intervalDays: Int
    ) {
        val uid = uidOrNull() ?: return
        val now = System.currentTimeMillis()
        val payload = hashMapOf(
            "userId" to uid,
            "name" to name,
            "dosage" to dosage,
            "quantityRemaining" to quantityRemaining,
            "frequency" to frequency,
            "daysOfWeek" to daysOfWeek,
            "times" to times,
            "isCourse" to isCourse,
            "endDate" to endDate,
            "isRefillReminder" to isRefillReminder,
            "refillThreshold" to refillThreshold,
            "intervalDays" to intervalDays,
            "createdAt" to now,
            "isDeleted" to false
        )
        medsPath(uid).add(payload)
            .addOnSuccessListener { Log.d(TAG, "addMedicine ok id=${it.id}") }
            .addOnFailureListener { Log.e(TAG, "addMedicine failed", it) }
    }

    fun updateMedicine(medId: String, updates: Map<String, Any?>, onDone: (Boolean) -> Unit) {
        val uid = uidOrNull() ?: return onDone(false)
        medsPath(uid).document(medId)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { e -> Log.e(TAG, "updateMedicine failed", e); onDone(false) }
    }

    /**
     * ใช้ตอนผู้ใช้ “เพิ่มจำนวนยาใหม่ในวันนี้” ให้เลื่อนคอร์สให้เริ่มนับจากวันนี้
     * และคำนวณ endDate ใหม่ = startToday + (จำนวนเม็ดใหม่ / ต่อวัน - 1)
     */
    fun extendCourseFromToday(medId: String, newQuantity: Int, onDone: (Boolean) -> Unit) {
        val uid = uidOrNull() ?: return onDone(false)
        val med = _items.value.firstOrNull { it.id == medId }
        if (med == null) return onDone(true) // ไม่เจอในแคช ก็ข้าม

        val perDay = max(1, med.times.size)
        val days = max(1, (newQuantity + perDay - 1) / perDay) // ปัดขึ้น
        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = todayCal.timeInMillis
        val end = (todayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, days - 1) }.timeInMillis

        val payload = mapOf(
            "isCourse" to true,
            "courseStart" to start,
            "endDate" to end,
            "quantityRemaining" to newQuantity
        )
        medsPath(uid).document(medId).set(payload, SetOptions.merge())
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { e -> Log.e(TAG, "extendCourseFromToday failed", e); onDone(false) }
    }

    /* one-shot fetch */
    fun loadMedicineByIdOnce(medId: String, onResult: (Medicine?) -> Unit) {
        val uid = uidOrNull() ?: return onResult(null)
        medsPath(uid).document(medId).get()
            .addOnSuccessListener { snap -> onResult(Medicine.fromDoc(snap)) }
            .addOnFailureListener { _ -> onResult(null) }
    }

    /* ------------ per-date helpers ------------ */

    private fun dateKey(cal: Calendar) =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.time)

    /* ------------ Taken per date ------------ */

    fun listenTakenFor(date: Calendar) {
        val uid = uidOrNull() ?: return
        takenReg?.remove()
        val key = dateKey(date)
        takenReg = db.collection("users").document(uid)
            .collection("taken").document(key)
            .addSnapshotListener { doc, err ->
                if (err != null) {
                    Log.e(TAG, "listenTakenFor error", err)
                    _takenForDate.value = emptySet()
                    return@addSnapshotListener
                }
                val ids = (doc?.get("medIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                _takenForDate.value = ids.toSet()
            }
    }

    fun setTakenFor(date: Calendar, med: Medicine, taken: Boolean) {
        val uid = uidOrNull() ?: return
        val key = dateKey(date)

        val takenDoc = db.collection("users").document(uid)
            .collection("taken").document(key)

        val histId = "${uid}_${key}_${med.id}"

        val cal = (date.clone() as Calendar).apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val ts = runCatching {
            val parts = (med.times.firstOrNull() ?: "12:00").split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: 12
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            cal.set(Calendar.HOUR_OF_DAY, h)
            cal.set(Calendar.MINUTE, m)
            cal.timeInMillis
        }.getOrElse { System.currentTimeMillis() }

        val historyData = mapOf(
            "userId" to uid,
            "medicineId" to med.id,
            "medicineName" to med.name,
            "status" to if (taken) "taken" else "skipped",
            "time" to (med.times.firstOrNull() ?: "--:--"),
            "timestamp" to ts
        )

        val batch = db.batch()

        if (taken) {
            batch.set(
                takenDoc,
                mapOf("medIds" to FieldValue.arrayUnion(med.id)),
                SetOptions.merge()
            )
        } else {
            batch.set(
                takenDoc,
                mapOf("medIds" to FieldValue.arrayRemove(med.id)),
                SetOptions.merge()
            )
        }

        // history (global)
        batch.set(
            db.collection("history").document(histId),
            historyData,
            SetOptions.merge()
        )
        // history (under user)
        batch.set(
            db.collection("users").document(uid)
                .collection("history").document(histId),
            historyData,
            SetOptions.merge()
        )

        batch.commit()
            .addOnFailureListener { Log.e(TAG, "setTakenFor batch failed", it) }
    }

    /* ------------ Cancel alarm (skip today/all) ------------ */

    fun listenCancelledFor(date: Calendar) {
        val uid = uidOrNull() ?: return
        cancelledReg?.remove()
        val key = dateKey(date)
        cancelledReg = db.collection("users").document(uid)
            .collection("cancelled").document(key)
            .addSnapshotListener { doc, err ->
                if (err != null) {
                    Log.e(TAG, "listenCancelledFor error", err)
                    _cancelledForDate.value = emptySet()
                    return@addSnapshotListener
                }
                val ids = (doc?.get("medIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                _cancelledForDate.value = ids.toSet()
            }
    }

    fun cancelAlarmForToday(date: Calendar, medId: String, onDone: (Boolean) -> Unit = {}) {
        val uid = uidOrNull() ?: return
        val key = dateKey(date)
        val doc = db.collection("users").document(uid)
            .collection("cancelled").document(key)
        doc.set(mapOf("medIds" to FieldValue.arrayUnion(medId)), SetOptions.merge())
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { e -> Log.e(TAG, "cancelAlarmForToday failed", e); onDone(false) }
    }

    fun cancelAllAlarmsForMedicine(medId: String, onDone: (Boolean) -> Unit = {}) {
        val uid = uidOrNull() ?: return
        medsPath(uid).document(medId)
            .update("times", emptyList<String>())
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { e -> Log.e(TAG, "cancelAllAlarmsForMedicine failed", e); onDone(false) }
    }

    /* ------------ stock helpers ------------ */

    private fun dosesPerDay(med: Medicine) = max(1, med.times.size)

    private fun parsePillPerDose(dosage: String): Int {
        val t = dosage.trim()
        Regex("""[×xX\*]\s*(\d+)\s*เม็ด\s*/\s*ครั้ง""").find(t)?.let { return it.groupValues[1].toIntOrNull() ?: 1 }
        Regex("""ครั้งละ\s*(\d+)\s*เม็ด""").find(t)?.let { return it.groupValues[1].toIntOrNull() ?: 1 }
        Regex("""(\d+)\s*เม็ด\s*/\s*ครั้ง""").find(t)?.let { return it.groupValues[1].toIntOrNull() ?: 1 }
        Regex("""(\d+)\s*เม็ด""").find(t)?.let { return it.groupValues[1].toIntOrNull() ?: 1 }
        return 1
    }

    private fun stockUnitsPerDay(med: Medicine): Int {
        val perDay = dosesPerDay(med)
        val isLiquid = med.dosage.contains("mL/ครั้ง")
        return if (isLiquid) max(1, perDay) else parsePillPerDose(med.dosage).coerceAtLeast(1) * perDay
    }

    fun markTakenForToday(med: Medicine, onDone: (Boolean) -> Unit = {}) {
        val uid = uidOrNull() ?: return
        val amount = stockUnitsPerDay(med)
        val newQty = (med.quantityRemaining - amount).coerceAtLeast(0)
        viewModelScope.launch {
            _items.value = _items.value.map { if (it.id == med.id) it.copy(quantityRemaining = newQty) else it }
        }
        medsPath(uid).document(med.id).update("quantityRemaining", newQty)
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { Log.e(TAG, "markTakenForToday failed", it); refreshOnce(); onDone(false) }
    }

    fun undoTakenForToday(med: Medicine, onDone: (Boolean) -> Unit = {}) {
        val uid = uidOrNull() ?: return
        val amount = stockUnitsPerDay(med)
        val newQty = (med.quantityRemaining + amount)
        viewModelScope.launch {
            _items.value = _items.value.map { if (it.id == med.id) it.copy(quantityRemaining = newQty) else it }
        }
        medsPath(uid).document(med.id).update("quantityRemaining", newQty)
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { Log.e(TAG, "undoTakenForToday failed", it); refreshOnce(); onDone(false) }
    }

    /* ====================================================================== */
    /* ======================  EVENTS: 1 ปลุก = 1 เอกสาร  ==================== */
    /* ====================================================================== */

    /** โฟลเดอร์เก็บ event รายวัน (users/{uid}/events/{yyyyMMdd}/{yyyyMMdd}/{eventId}) */
    private fun eventsCol(uid: String, dateKey: String) =
        db.collection("users").document(uid)
            .collection("events").document(dateKey)
            .collection(dateKey)

    /** แปลง "HH:mm" -> Pair(h, m) */
    private fun parseHHmm(t: String): Pair<Int, Int> {
        val p = t.split(":")
        return (p.getOrNull(0)?.toIntOrNull() ?: 8) to (p.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    /** ตรวจว่าตามความถี่ ยานี้กินใน 'date' ไหม (รองรับ ทุกวัน/วันเว้นวัน/เลือกวัน) */
    private fun qualifiesByFrequencyForDate(med: Medicine, date: Calendar): Boolean {
        val label = med.frequency.lowercase(Locale.ROOT)
        return when {
            label.startsWith("ทุกวัน") -> true
            label.startsWith("วันเว้นวัน") -> {
                val start = Calendar.getInstance().apply {
                    timeInMillis = med.courseStart ?: med.createdAt
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                val sel = (date.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                val diff = ((sel.timeInMillis - start.timeInMillis) / 86_400_000L).toInt()
                diff >= 0 && diff % 2 == 0
            }
            label.startsWith("เลือกวัน") -> {
                val th = arrayOf("", "อา.", "จ.", "อ.", "พ.", "พฤ.", "ศ.", "ส.")
                val key = th[date.get(Calendar.DAY_OF_WEEK)]
                med.daysOfWeek.contains(key)
            }
            else -> true
        }
    }

    /** จำนวนวันจากสต็อก (ปัดขึ้น) เพื่อกำหนดว่าจะสร้าง events ล่วงหน้ากี่วัน */
    private fun daysFromStock(med: Medicine): Int {
        val perDay = max(1, med.times.size)
        if (perDay <= 0) return 0
        val d = (med.quantityRemaining + perDay - 1) / perDay
        return max(1, d)
    }

    /** ✅ สร้าง/อัปเดต Event รายวัน×เวลา จากวันนี้ไปตาม stock */
    fun upsertEventsFromToday(med: Medicine, onDone: (Boolean, String?) -> Unit = { _, _ -> }) {
        val uid = uidOrNull() ?: return
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val days = daysFromStock(med)
        if (days == 0 || med.times.isEmpty()) {
            onDone(true, null); return
        }

        viewModelScope.launch {
            try {
                for (i in 0 until days) {
                    val c = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i) }
                    if (!qualifiesByFrequencyForDate(med, c)) continue
                    val dk = dateKey(c)
                    val batch = db.batch()
                    for (t in med.times.sorted()) {
                        val (h, m) = parseHHmm(t)
                        val ts = (c.clone() as Calendar).apply {
                            set(Calendar.HOUR_OF_DAY, h)
                            set(Calendar.MINUTE, m)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        val eventId = "${dk}_${med.id}_${t.replace(":", "")}"
                        val ref = eventsCol(uid, dk).document(eventId)
                        val data = mapOf(
                            "userId" to uid,
                            "medicineId" to med.id,
                            "name" to med.name,
                            "time" to t,
                            "dateKey" to dk,
                            "timestamp" to ts
                        )
                        batch.set(ref, data, SetOptions.merge())
                    }
                    batch.commit().await()
                }
                onDone(true, null)
            } catch (e: Exception) {
                Log.e(TAG, "upsertEventsFromToday failed", e)
                onDone(false, e.message)
            }
        }
    }

    /** ✅ ลบ Event เฉพาะ “วันนี้” ของยานี้ (ทุกเวลาในวันนี้) */
    fun deleteTodayEventsForMedicine(medId: String, onDone: (Boolean, String?) -> Unit = { _, _ -> }) {
        val uid = uidOrNull() ?: return
        val todayKey = dateKey(Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        })
        viewModelScope.launch {
            try {
                val qs = eventsCol(uid, todayKey).whereEqualTo("medicineId", medId).get().await()
                if (qs.isEmpty) { onDone(true, null); return@launch }
                val batch = db.batch()
                qs.documents.forEach { d -> batch.delete(d.reference) }
                batch.commit().await()
                onDone(true, null)
            } catch (e: Exception) {
                Log.e(TAG, "deleteTodayEventsForMedicine failed", e)
                onDone(false, e.message)
            }
        }
    }

    /** ✅ ลบ Event “ทั้งหมด” ของยานี้ (ทุกวัน ทุกเวลา) */
    fun deleteAllEventsForMedicine(medId: String, onDone: (Boolean, String?) -> Unit = { _, _ -> }) {
        val uid = uidOrNull() ?: return
        viewModelScope.launch {
            try {
                val start = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                // ลบล่วงหน้า 365 วัน (ปรับได้ตามที่ต้องการ)
                for (i in 0 until 365) {
                    val c = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i) }
                    val dk = dateKey(c)
                    val qs = eventsCol(uid, dk).whereEqualTo("medicineId", medId).get().await()
                    if (qs.isEmpty) continue
                    val batch = db.batch()
                    qs.documents.forEach { d -> batch.delete(d.reference) }
                    batch.commit().await()
                }
                onDone(true, null)
            } catch (e: Exception) {
                Log.e(TAG, "deleteAllEventsForMedicine failed", e)
                onDone(false, e.message)
            }
        }
    }

    /* -------------------- Helpers สำหรับดีบักแบบรวมทุกยา -------------------- */

    /** สร้าง/อัปเดต Event ของ "ยาทุกตัว" สำหรับวันนี้ แล้วเรียก onDone เมื่อเสร็จ */
    fun upsertAllEventsForToday(onDone: (Boolean) -> Unit = {}) {
        val meds = _items.value
        if (meds.isEmpty()) { onDone(false); return }
        var okAll = true
        var left = meds.size
        meds.forEach { med ->
            upsertEventsFromToday(med) { ok, _ ->
                if (!ok) okAll = false
                left--
                if (left == 0) onDone(okAll)
            }
        }
    }

    /** ลบ Event "วันนี้" ของยาทุกตัว แล้วเรียก onDone เมื่อเสร็จ */
    fun deleteAllTodayEvents(onDone: (Boolean) -> Unit = {}) {
        val meds = _items.value
        if (meds.isEmpty()) { onDone(true); return }
        var okAll = true
        var left = meds.size
        meds.forEach { med ->
            deleteTodayEventsForMedicine(med.id) { ok, _ ->
                if (!ok) okAll = false
                left--
                if (left == 0) onDone(okAll)
            }
        }
    }

    /* ====================================================================== */

    override fun onCleared() {
        super.onCleared()
        reg?.remove()
        takenReg?.remove()
        cancelledReg?.remove()
    }
}

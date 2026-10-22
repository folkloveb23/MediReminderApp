package com.example.medireminderapp

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "CalendarMonthScreen"

data class HistoryRecord(
    val id: String = "",
    val medicineName: String = "",
    val status: String = "",
    val time: String = "",
    val timestamp: Long = 0L
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarMonthScreen() {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()

    var currentMonth by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedDate by remember { mutableStateOf(Date()) }

    var records by remember { mutableStateOf<List<HistoryRecord>>(emptyList()) }
    var dailyNote by remember { mutableStateOf("") }

    val monthFormat = remember { SimpleDateFormat("MMMM", Locale("th", "TH")) }
    val yearFormat = remember { SimpleDateFormat("yyyy", Locale("th", "TH")) }

    suspend fun loadHistory(date: Date) {
        val uid = auth.currentUser?.uid ?: return
        try {
            // ช่วงเริ่ม-จบวัน (local)
            val cal = Calendar.getInstance().apply {
                time = date
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            cal.add(Calendar.DAY_OF_MONTH, 1)
            val end = cal.timeInMillis

            Log.d(TAG, "loadHistory uid=$uid start=$start end=$end")

            // ✅ อ่านจาก users/{uid}/history (ไม่ต้องใช้ composite index)
            val snap = db.collection("users").document(uid)
                .collection("history")
                .whereGreaterThanOrEqualTo("timestamp", start)
                .whereLessThan("timestamp", end)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get().await()

            records = snap.documents.mapNotNull {
                val ts = it.getLong("timestamp") ?: return@mapNotNull null
                val name = it.getString("medicineName") ?: return@mapNotNull null
                HistoryRecord(
                    id = it.id,
                    medicineName = name,
                    status = it.getString("status") ?: "",
                    time = it.getString("time") ?: "--:--",
                    timestamp = ts
                )
            }
            Log.d(TAG, "history count=${records.size}")

            // โหลดบันทึกประจำวัน
            val dateKey = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(date)
            val noteSnap = db.collection("daily_notes")
                .document("${uid}_$dateKey")
                .get().await()
            dailyNote = noteSnap.getString("note") ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "loadHistory error", e)
            records = emptyList()
        }
    }

    suspend fun saveDailyNote() {
        val uid = auth.currentUser?.uid ?: return
        val dateKey = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(selectedDate)
        val data = mapOf(
            "userId" to uid,
            "date" to dateKey,
            "note" to dailyNote,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("daily_notes").document("${uid}_$dateKey")
            .set(data, SetOptions.merge()).await()
        Toast.makeText(context, "บันทึกเรียบร้อย ✅", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(selectedDate) {
        scope.launch { loadHistory(selectedDate) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F6F4))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        /* เดือน */
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                currentMonth = (currentMonth.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
            }) { Icon(Icons.Default.ArrowBack, contentDescription = "Prev") }

            Text(
                "${monthFormat.format(currentMonth.time)} ${yearFormat.format(currentMonth.time)}",
                fontWeight = FontWeight.Bold, fontSize = 22.sp
            )

            IconButton(onClick = {
                currentMonth = (currentMonth.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
            }) { Icon(Icons.Default.ArrowForward, contentDescription = "Next") }
        }

        Spacer(Modifier.height(8.dp))

        /* ปฏิทิน */
        CalendarGrid(
            currentMonth = currentMonth,
            selectedDate = selectedDate,
            onSelect = { date -> selectedDate = date }
        )

        Spacer(Modifier.height(12.dp))

        /* ประวัติ */
        Text("ประวัติ", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())

        if (records.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                Text("ยังไม่มีประวัติในวันนี้", color = Color.Gray)
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f, false)) {
                items(records) { rec -> HistoryCardItem(rec) }
            }
        }

        Spacer(Modifier.height(12.dp))

        /* บันทึกประจำวัน */
        Text("บันทึกประจำวัน", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = dailyNote,
            onValueChange = { dailyNote = it },
            placeholder = { Text("พิมพ์ข้อความ เช่น วันนี้รู้สึกดี...") },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(16.dp))
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { scope.launch { saveDailyNote() } },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
        ) {
            Text("บันทึกข้อความ", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

/* ตารางวันที่ */
@Composable
fun CalendarGrid(currentMonth: Calendar, selectedDate: Date, onSelect: (Date) -> Unit) {
    val daysInMonth = currentMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
    val today = Calendar.getInstance()
    val startDayOfWeek = Calendar.getInstance().apply {
        time = currentMonth.time
        set(Calendar.DAY_OF_MONTH, 1)
    }.get(Calendar.DAY_OF_WEEK) - 1

    val weeks = mutableListOf<List<Int?>>()
    var day = 1
    while (day <= daysInMonth) {
        val week = mutableListOf<Int?>()
        for (i in 0..6) {
            if (weeks.isEmpty() && i < startDayOfWeek) week.add(null)
            else if (day <= daysInMonth) week.add(day++)
            else week.add(null)
        }
        weeks.add(week)
    }

    val selectedCal = Calendar.getInstance().apply { time = selectedDate }

    weeks.forEach { week ->
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            week.forEach { d ->
                if (d != null) {
                    val date = Calendar.getInstance().apply {
                        time = currentMonth.time
                        set(Calendar.DAY_OF_MONTH, d)
                    }
                    val isToday = d == today.get(Calendar.DAY_OF_MONTH)
                            && currentMonth.get(Calendar.MONTH) == today.get(Calendar.MONTH)
                            && currentMonth.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                    val isSelected = d == selectedCal.get(Calendar.DAY_OF_MONTH)
                            && currentMonth.get(Calendar.MONTH) == selectedCal.get(Calendar.MONTH)
                            && currentMonth.get(Calendar.YEAR) == selectedCal.get(Calendar.YEAR)

                    val bgColor = when {
                        isSelected -> Color(0xFF4CAF50)
                        isToday -> Color(0xFFB2DFDB)
                        else -> Color.Transparent
                    }

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(bgColor)
                            .clickable { onSelect(date.time) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            d.toString(),
                            color = if (isSelected) Color.White else Color.Black,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                } else {
                    Spacer(Modifier.size(42.dp))
                }
            }
        }
    }
}

/* ไอเท็มประวัติ */
@Composable
fun HistoryCardItem(record: HistoryRecord) {
    val chipBg = when (record.status.lowercase(Locale.ROOT)) {
        "taken" -> Color(0xFFE8F5E9)
        "skipped" -> Color(0xFFFFEBEE)
        else -> Color(0xFFE0E0E0)
    }
    val chipText = when (record.status.lowercase(Locale.ROOT)) {
        "taken" -> Color(0xFF2E7D32)
        "skipped" -> Color(0xFFC62828)
        else -> Color(0xFF424242)
    }
    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(record.medicineName, fontWeight = FontWeight.SemiBold)
                val hhmm = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(record.timestamp))
                Text("${record.time}  •  $hhmm", color = Color(0xFF607D8B), fontSize = 12.sp)
            }
            Surface(color = chipBg, shape = RoundedCornerShape(50)) {
                Text(
                    if (record.status == "taken") "Taken" else "Skipped",
                    color = chipText,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 12.sp, fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

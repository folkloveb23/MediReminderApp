@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.medireminderapp

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

@Composable
fun DashboardScreen(
    navController: NavHostController,
    vm: MedicinesViewModel = viewModel()
) {
    val ctx = LocalContext.current

    val medicines by vm.items.collectAsState()
    val today = remember { clearTime(Calendar.getInstance()) }
    var selectedDate by remember { mutableStateOf(clearTime(Calendar.getInstance())) }

    LaunchedEffect(Unit) { vm.listenMedicines() }
    LaunchedEffect(selectedDate) { vm.listenTakenFor(selectedDate) }
    val takenIds by vm.takenForDate.collectAsState()

    LaunchedEffect(selectedDate) { vm.listenCancelledFor(selectedDate) }
    val cancelledIds by vm.cancelledForDate.collectAsState()

    val sdfThai = remember { SimpleDateFormat("EEEE d MMM", Locale("th", "TH")) }

    val gestureModifier = Modifier.pointerInput(selectedDate) {
        detectHorizontalDragGestures { _, drag ->
            when {
                drag > 40  -> selectedDate =
                    (selectedDate.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -1); clearTime(this) }
                drag < -40 -> selectedDate =
                    (selectedDate.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH,  1); clearTime(this) }
            }
        }
    }

    val days = listOf(-1, 0, 1).map {
        (selectedDate.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, it); clearTime(this) }
    }

    val medsForSelectedDate = remember(medicines, selectedDate, today, cancelledIds) {
        medicines.filter { med ->
            med.times.isNotEmpty() &&
                    qualifiesByFrequency(med, selectedDate) &&
                    !selectedDate.before(today) &&
                    withinCourse(med, selectedDate) &&
                    !cancelledIds.contains(med.id)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(gestureModifier),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            days.forEach { day ->
                val isSelected = sameDay(day, selectedDate)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (isSelected)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    else
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    tonalElevation = if (isSelected) 3.dp else 0.dp,
                    modifier = Modifier
                        .widthIn(min = 76.dp)
                        .clickable { navController.navigate("calendar") }
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(SimpleDateFormat("EEE", Locale("th", "TH")).format(day.time), fontWeight = FontWeight.Bold)
                        Text(day.get(Calendar.DAY_OF_MONTH).toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            sdfThai.format(selectedDate.time),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )

        // ---------- ปุ่มช่วยดีบัก/ทดสอบ ----------
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { AlarmScheduler.scheduleDebugAfter(ctx, 15) }) {
                Text("ทดสอบเด้งใน 15 วิ")
            }
            OutlinedButton(onClick = {
                if (Build.VERSION.SDK_INT >= 26) {
                    val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    }
                    ctx.startActivity(i)
                }
            }) { Text("ตั้งค่าการแจ้งเตือน") }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = {
                vm.upsertAllEventsForToday { ok ->
                    if (ok) AlarmScheduler.scheduleToday(ctx)
                }
            }
        ) { Text("บังคับสร้าง events วันนี้ + ตั้งปลุก") }

        Spacer(Modifier.height(12.dp))
        // ----------------------------------------

        if (medsForSelectedDate.isEmpty()) {
            Text("ไม่มีรายการยาสำหรับวันนี้", color = MaterialTheme.colorScheme.outline)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp)
            ) {
                items(medsForSelectedDate, key = { it.id }) { med ->
                    val isTakenToday = takenIds.contains(med.id)
                    MedicineCard(
                        med = med,
                        isTaken = isTakenToday,
                        onToggleTaken = { newValue ->
                            // เดิม: บันทึกสถานะ "ทานแล้ว/ยัง"
                            vm.setTakenFor(selectedDate, med, newValue)

                            // เพิ่ม: ผูกสวิตช์กับการตั้ง/ยกเลิกแจ้งเตือนของ "วันนี้"
                            if (newValue) {
                                // เปิดสวิตช์ -> คำนวณ/สร้าง events ของวันนี้ แล้วตั้งปลุก
                                vm.upsertEventsFromToday(med) { ok, _ ->
                                    if (ok) AlarmScheduler.scheduleToday(ctx)
                                }
                            } else {
                                // ปิดสวิตช์ -> ลบ events ของวันนี้สำหรับยานี้ แล้วรีเซ็ตปลุกที่เหลือ
                                vm.deleteTodayEventsForMedicine(med.id) { ok, _ ->
                                    if (ok) AlarmScheduler.scheduleToday(ctx)
                                }
                            }
                        },
                        onEdit = { navController.navigate("edit/${med.id}") }
                    )
                }
            }
        }
    }
}

/* ---------------- Helpers ---------------- */

private fun clearTime(cal: Calendar = Calendar.getInstance()): Calendar {
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal
}

private fun sameDay(a: Calendar, b: Calendar) =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

private fun startOfDay(millis: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun withinCourse(med: Medicine, date: Calendar): Boolean {
    val start = startOfDay((med.courseStart ?: med.createdAt))
    val end   = med.endDate ?: Long.MAX_VALUE
    val day   = date.timeInMillis
    return day in start..end
}

private fun qualifiesByFrequency(med: Medicine, date: Calendar): Boolean {
    val label = med.frequency.lowercase(Locale.ROOT)
    return when {
        label.startsWith("ทุกวัน") -> true
        label.startsWith("วันเว้นวัน") -> {
            val base = startOfDay((med.courseStart ?: med.createdAt))
            val sel  = startOfDay(date.timeInMillis)
            val diffDays = ((sel - base) / 86_400_000L).toInt()
            diffDays >= 0 && diffDays % 2 == 0
        }
        label.startsWith("เลือกวัน") -> {
            val th = arrayOf("", "อา.", "จ.", "อ.", "พ.", "พฤ.", "ศ.", "ส.")
            val key = th[date.get(Calendar.DAY_OF_WEEK)]
            med.daysOfWeek.contains(key)
        }
        else -> true
    }
}

/* ---------------- UI Card ---------------- */

@Composable
fun MedicineCard(
    med: Medicine,
    isTaken: Boolean,
    onToggleTaken: (Boolean) -> Unit,
    onEdit: () -> Unit
) {
    var expanded by remember(med.id) { mutableStateOf(false) }
    val perDay = max(1, med.times.size)
    val isLiquid = med.dosage.contains("mL/ครั้ง")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(med.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    val sub = buildString {
                        append(med.dosage.ifBlank { "ไม่ระบุขนาดยา" })
                        if (med.times.isNotEmpty()) append(" • เวลา: ${med.times.joinToString(", ")}")
                    }
                    Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }

                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "แก้ไข") }

                var localTaken by remember(med.id, isTaken) { mutableStateOf(isTaken) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        med.times.firstOrNull() ?: "--:--",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Switch(
                        checked = localTaken,
                        onCheckedChange = { v -> localTaken = v; onToggleTaken(v) },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedTrackColor = MaterialTheme.colorScheme.error
                        )
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ความถี่: ${med.frequency} • ต่อวัน: $perDay",
                    color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                Text(if (expanded) "ย่อ ▲" else "ดูรายละเอียด ▼",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { expanded = !expanded })
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp)); Divider(); Spacer(Modifier.height(8.dp))
                Text("ชนิดยา: ${if (isLiquid) "ยาน้ำ" else "ยาเม็ด"}", fontSize = 13.sp)
                if (med.daysOfWeek.isNotEmpty()) {
                    Text("วันเฉพาะ: ${med.daysOfWeek.joinToString(", ")}", fontSize = 13.sp)
                }
            }
        }
    }
}

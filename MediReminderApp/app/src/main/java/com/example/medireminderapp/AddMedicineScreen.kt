@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.example.medireminderapp

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import java.util.*
import kotlin.math.floor

@Composable
fun AddMedicineScreen(
    navController: NavHostController,
    vm: MedicinesViewModel = viewModel(),
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var name by remember { mutableStateOf("") }
    var strengthValue by remember { mutableStateOf("") }
    var strengthUnit by remember { mutableStateOf("mg") }
    var times by remember { mutableStateOf(listOf<String>()) }

    var stockUnit by remember { mutableStateOf("PILL") }   // "PILL" | "ML"
    var initialQuantity by remember { mutableStateOf("") }
    var pillPerDose by remember { mutableStateOf("1") }
    var refillThreshold by remember { mutableStateOf("") }

    var bottleVolumeMl by remember { mutableStateOf("") }
    var dosePerIntakeMl by remember { mutableStateOf("") }
    var refillThresholdDoses by remember { mutableStateOf("") }

    // ✅ ใช้ตัวเลือก "วันเว้นวัน (ALT)" แทน วันคู่/คี่
    data class FreqOpt(val code: String, val label: String)
    val frequencyOptions = listOf(
        FreqOpt("DAILY",  "ทุกวัน (แจ้งเตือนจนกว่ายาจะหมด)"),
        FreqOpt("ALT",    "วันเว้นวัน (เริ่มวันนี้)"),
        FreqOpt("SELECT", "เลือกวัน")
    )
    var selectedFrequencyCode by remember { mutableStateOf(frequencyOptions.first().code) }
    val weekDays = listOf("จ.", "อ.", "พ.", "พฤ.", "ศ.", "ส.", "อา.")
    var selectedDays by remember { mutableStateOf(setOf<String>()) }

    var note by remember { mutableStateOf("") }

    var showTimeError by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var cameFromOcr by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        navController.currentBackStackEntry?.savedStateHandle?.let { sh ->
            var fromOcr = false
            fun <T> read(key: String, apply: (T) -> Unit) {
                val v = sh.get<T>(key)
                if (v != null) { fromOcr = true; apply(v) }
                sh.remove<T>(key)
            }
            read<String>("ocr_name") { name = it }
            read<String>("ocr_strength_value") { strengthValue = it }
            read<String>("ocr_strength_unit") { strengthUnit = it }
            read<String>("ocr_stock_unit") { stockUnit = it }
            read<String>("ocr_quantity") { initialQuantity = it }
            read<String>("ocr_refill_threshold") { refillThreshold = it }
            read<String>("ocr_bottle_volume_ml") { bottleVolumeMl = it }
            read<String>("ocr_dose_per_intake_ml") { dosePerIntakeMl = it }
            read<String>("ocr_refill_threshold_doses") { refillThresholdDoses = it }
            if (stockUnit == "ML" && refillThresholdDoses.isBlank()) refillThresholdDoses = "4"
            cameFromOcr = fromOcr
        }
    }

    val dosesAvailable by derivedStateOf {
        runCatching {
            val vol = bottleVolumeMl.toDouble()
            val per = dosePerIntakeMl.toDouble()
            if (vol > 0 && per > 0) floor(vol / per).toInt() else 0
        }.getOrElse { 0 }
    }

    AlertDialog(
        modifier = Modifier.fillMaxHeight(0.95f),
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("ระบุรายละเอียด") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                if (!cameFromOcr) {
                    OutlinedButton(
                        onClick = { if (!isSaving) navController.navigate("ocr_scan") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("สแกนจากรูป (OCR) เพื่อออโต้ฟิล")
                    }
                }

                Text("ข้อมูลยา", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("ชื่อยา") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = strengthValue,
                        onValueChange = { strengthValue = it },
                        label = { Text("ความแรง") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = strengthUnit,
                        onValueChange = { strengthUnit = it },
                        label = { Text("หน่วย") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Divider(Modifier.padding(vertical = 12.dp))

                Text("รูปแบบยา", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = stockUnit == "PILL", onClick = { stockUnit = "PILL" }, label = { Text("ยาเม็ด") })
                    FilterChip(selected = stockUnit == "ML", onClick = { stockUnit = "ML" }, label = { Text("ยาน้ำ") })
                }

                Divider(Modifier.padding(vertical = 12.dp))

                Text("สต็อกยา", style = MaterialTheme.typography.titleMedium)
                if (stockUnit == "PILL") {
                    OutlinedTextField(
                        value = initialQuantity,
                        onValueChange = { initialQuantity = it },
                        label = { Text("จำนวนยาเริ่มต้น (เม็ด)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pillPerDose,
                        onValueChange = { pillPerDose = it },
                        label = { Text("จำนวนกินต่อครั้ง (เม็ด)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = refillThreshold,
                        onValueChange = { refillThreshold = it },
                        label = { Text("แจ้งเตือนเมื่อเหลือ (เม็ด)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = bottleVolumeMl,
                            onValueChange = { bottleVolumeMl = it },
                            label = { Text("ปริมาตรทั้งหมด (mL)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = dosePerIntakeMl,
                            onValueChange = { dosePerIntakeMl = it },
                            label = { Text("ต่อครั้ง (mL)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("≈ ใช้ได้ประมาณ $dosesAvailable ครั้ง", color = Color.Gray, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = refillThresholdDoses,
                        onValueChange = { refillThresholdDoses = it },
                        label = { Text("แจ้งเตือนเมื่อเหลือ (ครั้ง)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Divider(Modifier.padding(vertical = 12.dp))

                Text("การแจ้งเตือนยา", style = MaterialTheme.typography.titleMedium)
                frequencyOptions.forEach { opt ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedFrequencyCode = opt.code }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedFrequencyCode == opt.code,
                            onClick = { selectedFrequencyCode = opt.code }
                        )
                        Text(opt.label)
                    }
                }
                if (selectedFrequencyCode == "SELECT") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        weekDays.forEach { day ->
                            FilterChip(
                                selected = selectedDays.contains(day),
                                onClick = {
                                    selectedDays = if (selectedDays.contains(day)) selectedDays - day else selectedDays + day
                                },
                                label = { Text(day) }
                            )
                        }
                    }
                }

                Divider(Modifier.padding(vertical = 12.dp))

                Text("เวลาที่ต้องเตือน", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    times.forEach { time ->
                        InputChip(
                            selected = false,
                            onClick = {},
                            label = { Text(time) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "ลบ",
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { times = times - time }
                                )
                            }
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance()
                        TimePickerDialog(
                            context,
                            { _, h, m ->
                                val timeStr = String.format("%02d:%02d", h, m)
                                if (!times.contains(timeStr)) times = (times + timeStr).sorted()
                                showTimeError = false
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            true
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "เพิ่มเวลา")
                    Spacer(Modifier.width(8.dp))
                    Text("เพิ่มเวลาเตือน")
                }
                if (showTimeError && times.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("กรุณาเพิ่มเวลาเตือนอย่างน้อย 1 เวลา", color = Color(0xFFD32F2F), fontSize = 12.sp)
                }

                Divider(Modifier.padding(vertical = 12.dp))

                Text("หมายเหตุ", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("เช่น กินหลังอาหาร, ห้ามทานพร้อมนม ฯลฯ") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving && name.isNotBlank() && times.isNotEmpty(),
                onClick = {
                    if (times.isEmpty() || name.isBlank()) {
                        showTimeError = times.isEmpty()
                        return@Button
                    }
                    isSaving = true

                    val qty = if (stockUnit == "ML") dosesAvailable else (initialQuantity.toIntOrNull() ?: 0)
                    val refill = if (stockUnit == "ML")
                        (refillThresholdDoses.toIntOrNull() ?: 0)
                    else
                        (refillThreshold.toIntOrNull() ?: 0)

                    val dosageStr = if (stockUnit == "ML") {
                        "${dosePerIntakeMl.ifBlank { "?" }} mL/ครั้ง"
                    } else {
                        val per = pillPerDose.ifBlank { "1" }
                        "${strengthValue.ifBlank { "?" }} ${strengthUnit.ifBlank { "mg" }} × $per เม็ด/ครั้ง"
                    }

                    // ✅ บันทึกข้อความความถี่ตาม ALT
                    val frequencyText = when (selectedFrequencyCode) {
                        "DAILY" -> "ทุกวัน (แจ้งเตือนจนกว่ายาจะหมด)"
                        "ALT"   -> "วันเว้นวัน (เริ่มวันนี้)"
                        else    -> "เลือกวัน"
                    }

                    vm.addMedicine(
                        name = name,
                        dosage = dosageStr,
                        quantityRemaining = qty,
                        frequency = frequencyText,
                        daysOfWeek = selectedDays.toList(),
                        times = times,
                        isCourse = false,
                        endDate = null,
                        isRefillReminder = true,
                        refillThreshold = refill,
                        intervalDays = 0
                    )

                    onDismiss()
                    navController.navigate("dashboard") { launchSingleTop = true }
                    isSaving = false
                }
            ) { Text("บันทึก") }
        },
        dismissButton = {
            TextButton(onClick = { if (!isSaving) onDismiss() }) { Text("ยกเลิก") }
        }
    )
}

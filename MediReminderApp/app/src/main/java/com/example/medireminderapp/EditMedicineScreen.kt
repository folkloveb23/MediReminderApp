@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.example.medireminderapp

import android.app.TimePickerDialog
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.navigation.NavHostController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

// ใช้สำหรับ Icon ขนาดเล็กในชิปเวลา
import androidx.compose.foundation.layout.size

private const val LOGTAG = "FIRESTORE_DEBUG"

@Composable
fun EditMedicineScreen(
    navController: NavHostController,
    medId: String,
    db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val scr = rememberScrollState()

    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var infoMsg  by remember { mutableStateOf<String?>(null) }

    var name by remember { mutableStateOf("") }

    // สต็อก/โดส (ฟิลด์หลักที่แก้บนหน้าจอนี้)
    var initialQuantity by remember { mutableStateOf("") }
    var pillPerDose by remember { mutableStateOf("1") }

    // เวลาตั้งปลุก
    var times by remember { mutableStateOf(listOf<String>()) }
    var showTimeError by remember { mutableStateOf(false) }

    // ความถี่
    data class FreqOpt(val code: String, val label: String)
    val frequencyOptions = listOf(
        FreqOpt("DAILY",  "ทุกวัน (แจ้งเตือนจนกว่ายาจะหมด)"),
        FreqOpt("ALT",    "วันเว้นวัน (เริ่มวันนี้)"),
        FreqOpt("SELECT", "เลือกวัน")
    )
    var frequencyCode by remember { mutableStateOf("DAILY") }
    val weekDays = listOf("จ.","อ.","พ.","พฤ.","ศ.","ส.","อา.")
    var selectedDays by remember { mutableStateOf(setOf<String>()) }

    var note by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    // ไดอะล็อกลบการตั้งปลุก (ครั้งวันนี้/ทั้งชุด)
    var showDeleteAlarmDialog by remember { mutableStateOf(false) }

    // ---------- โหลด ----------
    LaunchedEffect(medId) {
        try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
                ?: run {
                    errorMsg = "ยังไม่ได้เข้าสู่ระบบ"
                    loading = false
                    return@LaunchedEffect
                }

            val path = "users/$uid/medicines/$medId"
            Log.d(LOGTAG, "LOAD medicine -> $path")

            val doc = db.collection("users").document(uid)
                .collection("medicines").document(medId)
                .get().await()

            if (!doc.exists()) {
                errorMsg = "ไม่พบรายการยา"
                loading = false
                return@LaunchedEffect
            }

            name = doc.getString("name").orEmpty()
            val dosage = doc.getString("dosage").orEmpty()
            val qtyRemaining = (doc.getLong("quantityRemaining") ?: 0).toInt()
            val freqText = doc.getString("frequency").orEmpty()

            @Suppress("UNCHECKED_CAST")
            times = (doc.get("times") as? List<String>)?.sorted().orEmpty()

            @Suppress("UNCHECKED_CAST")
            val dows = (doc.get("daysOfWeek") as? List<String>).orEmpty()
            selectedDays = dows.toSet()
            note = doc.getString("note").orEmpty()

            // ดึง per-dose แบบคร่าว ๆ
            Regex("""×\s*(\d+)\s*เม็ด\s*/\s*ครั้ง""").find(dosage)?.let {
                pillPerDose = it.groupValues[1]
            }
            initialQuantity = qtyRemaining.toString()

            frequencyCode = when {
                freqText.startsWith("ทุกวัน")    -> "DAILY"
                freqText.startsWith("วันเว้นวัน") -> "ALT"
                freqText.startsWith("เลือกวัน")   -> "SELECT"
                else -> "DAILY"
            }

            loading = false
        } catch (e: Exception) {
            Log.e(LOGTAG, "load failed", e)
            errorMsg = e.message ?: "เกิดข้อผิดพลาด"
            loading = false
        }
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (errorMsg != null) {
        AlertDialog(
            onDismissRequest = { errorMsg = null },
            title = { Text("ผิดพลาด") },
            text  = { Text(errorMsg!!) },
            confirmButton = { TextButton(onClick = { errorMsg = null }) { Text("ปิด") } }
        )
    }
    if (infoMsg != null) {
        AlertDialog(
            onDismissRequest = { infoMsg = null },
            title = { Text("สำเร็จ") },
            text  = { Text(infoMsg!!) },
            confirmButton = { TextButton(onClick = { infoMsg = null }) { Text("ตกลง") } }
        )
    }

    // ---------- UI ----------
    AlertDialog(
        modifier = Modifier.fillMaxHeight(0.95f),
        onDismissRequest = { if (!isSaving) navController.popBackStack() },
        title = { Text("แก้ไขยา: $name") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(scr)) {

                // คงเหลือ / เม็ดต่อครั้ง
                OutlinedTextField(
                    value = initialQuantity, onValueChange = { initialQuantity = it },
                    label = { Text("คงเหลือ (เม็ด)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pillPerDose, onValueChange = { pillPerDose = it },
                    label = { Text("จำนวนต่อครั้ง (เม็ด)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Divider(Modifier.padding(vertical = 12.dp))

                // ความถี่
                Text("การแจ้งเตือนยา", style = MaterialTheme.typography.titleMedium)
                frequencyOptions.forEach { opt ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { frequencyCode = opt.code }
                    ) {
                        RadioButton(selected = frequencyCode == opt.code, onClick = { frequencyCode = opt.code })
                        Text(opt.label)
                    }
                }
                if (frequencyCode == "SELECT") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        weekDays.forEach { d ->
                            FilterChip(
                                selected = selectedDays.contains(d),
                                onClick = {
                                    selectedDays =
                                        if (selectedDays.contains(d)) selectedDays - d else selectedDays + d
                                },
                                label = { Text(d) }
                            )
                        }
                    }
                }

                Divider(Modifier.padding(vertical = 12.dp))

                // เวลาเตือน
                Text("เวลาที่ต้องเตือน", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    times.forEach { t ->
                        InputChip(
                            selected = false, onClick = {}, label = { Text(t) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close, contentDescription = "ลบ",
                                    modifier = Modifier.size(18.dp).clickable {
                                        // ลบเวลาออกจากชุดเวลาที่แจ้งเตือน (ยังไม่ใช่ลบการ์ด)
                                        times = times - t
                                    }
                                )
                            }
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance()
                        TimePickerDialog(
                            ctx,
                            { _, h, m ->
                                val ts = "%02d:%02d".format(h, m)
                                if (!times.contains(ts)) times = (times + ts).sorted()
                                showTimeError = false
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            true
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("เพิ่มเวลาเตือน")
                }
                if (showTimeError && times.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("กรุณาเพิ่มเวลาอย่างน้อย 1 เวลา", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                Divider(Modifier.padding(vertical = 12.dp))

                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    placeholder = { Text("หมายเหตุ (เช่น กินหลังอาหาร)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                )
            }
        },
        // ปุ่มล่าง: ยกเลิก | ลบ | บันทึก
        confirmButton = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { if (!isSaving) navController.popBackStack() }) { Text("ยกเลิก") }

                // ปุ่มลบ (แดง พื้นหลังแดง ตัวอักษรขาว)
                Button(
                    onClick = { showDeleteAlarmDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F),
                        contentColor = Color.White
                    ),
                    shape = MaterialTheme.shapes.large
                ) { Text("ลบ") }

                Button(
                    enabled = !isSaving && name.isNotBlank(),
                    onClick = {
                        if (times.isEmpty()) { showTimeError = true; return@Button }
                        isSaving = true
                        scope.launch {
                            try {
                                val uid = FirebaseAuth.getInstance().currentUser?.uid
                                    ?: run {
                                        isSaving = false
                                        errorMsg = "ยังไม่ได้เข้าสู่ระบบ"
                                        return@launch
                                    }

                                val dosageStr = "× ${pillPerDose.ifBlank { "1" }} เม็ด/ครั้ง"
                                val freqText = when (frequencyCode) {
                                    "DAILY" -> "ทุกวัน (แจ้งเตือนจนกว่ายาจะหมด)"
                                    "ALT"   -> "วันเว้นวัน (เริ่มวันนี้)"
                                    else    -> "เลือกวัน"
                                }
                                val payload = hashMapOf(
                                    "dosage" to dosageStr,
                                    "quantityRemaining" to (initialQuantity.toIntOrNull() ?: 0),
                                    "frequency" to freqText,
                                    "times" to times,
                                    "daysOfWeek" to selectedDays.toList(),
                                    "note" to note
                                )

                                val path = "users/$uid/medicines/$medId"
                                Log.d(LOGTAG, "UPDATE medicine -> $path | payload=$payload")

                                db.collection("users").document(uid)
                                    .collection("medicines").document(medId)
                                    .set(payload, SetOptions.merge())
                                    .await()

                                isSaving = false
                                navController.popBackStack()
                            } catch (e: Exception) {
                                Log.e(LOGTAG, "save failed", e)
                                isSaving = false
                                errorMsg = e.message ?: "บันทึกไม่สำเร็จ"
                            }
                        }
                    }
                ) { Text("บันทึกการแก้ไข") }
            }
        },
        dismissButton = {}
    )

    // ---------- ไดอะล็อกลบการตั้งปลุก (เรียงบนลงล่าง + ปุ่มลบสีแดง + try/catch) ----------
    if (showDeleteAlarmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAlarmDialog = false },
            title = { Text("ลบการตั้งปลุก") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text("เลือกวิธีลบการตั้งปลุกของยานี้")
                    Spacer(Modifier.height(12.dp))

                    // 1) ลบการตั้งปลุกนี้ (เฉพาะวันนี้) — ไม่เด้งออกจากหน้า
                    TextButton(
                        onClick = {
                            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@TextButton
                            scope.launch {
                                try {
                                    val todayKey = SimpleDateFormat("yyyyMMdd", Locale.US)
                                        .format(clearTime(Calendar.getInstance()).time)
                                    val path = "users/$uid/skips/$todayKey"
                                    Log.d(LOGTAG, "ADD skip (today only) -> $path | medId=$medId")

                                    db.collection("users").document(uid)
                                        .collection("skips").document(todayKey)
                                        .set(
                                            mapOf("medIds" to FieldValue.arrayUnion(medId)),
                                            SetOptions.merge()
                                        )
                                        .await()

                                    showDeleteAlarmDialog = false
                                    infoMsg = "ลบการตั้งปลุกของยานี้ \"เฉพาะวันนี้\" แล้ว"
                                    // ไม่ popBackStack เพื่อไม่เด้งออกจากหน้า
                                } catch (e: Exception) {
                                    Log.e(LOGTAG, "skip today failed", e)
                                    errorMsg = "ลบการตั้งปลุกวันนี้ไม่สำเร็จ: ${e.message ?: "ไม่ทราบสาเหตุ"}"
                                    showDeleteAlarmDialog = false
                                }
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD32F2F)),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("ลบการตั้งปลุกนี้ (เฉพาะวันนี้)") }

                    Spacer(Modifier.height(6.dp))

                    // 2) ลบทั้งชุด (ซีรีส์) = ลบเอกสารยานี้ — จะกลับหน้าก่อนหน้าเมื่อสำเร็จ
                    TextButton(
                        onClick = {
                            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@TextButton
                            scope.launch {
                                try {
                                    val path = "users/$uid/medicines/$medId"
                                    Log.d(LOGTAG, "DELETE series -> $path")

                                    db.collection("users").document(uid)
                                        .collection("medicines").document(medId)
                                        .delete()
                                        .await()

                                    showDeleteAlarmDialog = false
                                    navController.popBackStack()
                                } catch (e: Exception) {
                                    Log.e(LOGTAG, "delete series failed", e)
                                    errorMsg = "ลบการตั้งปลุกทั้งหมดไม่สำเร็จ: ${e.message ?: "ไม่ทราบสาเหตุ"}"
                                    showDeleteAlarmDialog = false
                                }
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD32F2F)),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("ลบการตั้งปลุกสำหรับยานี้ทั้งหมด") }

                    Spacer(Modifier.height(6.dp))

                    // 3) ยกเลิก
                    TextButton(
                        onClick = { showDeleteAlarmDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("ยกเลิก") }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }
}

// helper
private fun clearTime(cal: Calendar): Calendar {
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal
}

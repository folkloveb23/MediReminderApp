package com.example.medireminderapp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AddMedicineDialog(
    onDismiss: () -> Unit,
    onConfirm: (Medicine) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var timesText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val med = Medicine(
                    name = name.trim(),
                    dosage = dosage.trim(),
                    // NOTE: frequency ให้เป็น "ไทย" ให้ตรงกับ Dashboard/ScheduleUtils
                    frequency = "ทุกวัน (แจ้งเตือนจนกว่ายาจะหมด)",
                    times = timesText.split(",").mapNotNull { it.trim().ifBlank { null } },
                    quantityRemaining = quantity.toIntOrNull() ?: 0
                )
                onConfirm(med)
            }) { Text("เพิ่ม") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ยกเลิก") } },
        title = { Text("เพิ่มยา (แบบเร็ว)") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("ชื่อยา") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = dosage, onValueChange = { dosage = it }, label = { Text("ขนาดยา/คำอธิบาย") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = quantity, onValueChange = { quantity = it }, label = { Text("จำนวนคงเหลือ (เม็ด/ครั้ง)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = timesText, onValueChange = { timesText = it }, label = { Text("เวลา (เช่น 08:00,20:00)") }, modifier = Modifier.fillMaxWidth())
                Text(
                    "ความถี่จะถูกตั้งเป็น “ทุกวัน (แจ้งเตือนจนกว่ายาจะหมด)” โดยอัตโนมัติ เพื่อให้ตรงกับตัวเช็คใน Dashboard",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}

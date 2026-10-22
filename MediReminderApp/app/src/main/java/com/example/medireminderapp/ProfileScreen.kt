package com.example.medireminderapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavHostController,
    vm: MedicinesViewModel = viewModel()
) {
    val auth = FirebaseAuth.getInstance()

    // ใช้รายการยาจาก VM แบบเรียลไทม์
    LaunchedEffect(Unit) { vm.listenMedicines() }
    val meds by vm.items.collectAsState()

    // ⬅️ แก้จุด error: ไม่ต้องใช้ isDeleted แล้ว
    val activeMeds = remember(meds) {
        meds.filter { it.quantityRemaining > 0 }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F8E9))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "โปรไฟล์",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF1B5E20)
        )
        Spacer(Modifier.height(12.dp))

        // ข้อมูลบัญชี
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("ข้อมูลบัญชี", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(6.dp))
                Text("UID: ${auth.currentUser?.uid ?: "-"}")
                Text("อีเมล: ${auth.currentUser?.email ?: "-"}")
                Text("Anonymous: ${auth.currentUser?.isAnonymous ?: false}")
                Text("ผู้ให้บริการ: ${auth.currentUser?.providerData?.joinToString { it.providerId } ?: "-"}")
            }
        }

        Spacer(Modifier.height(20.dp))

        // ยาที่กำลังใช้อยู่
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "ยาที่กำลังใช้อยู่",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF2E7D32)
                )
                Spacer(Modifier.height(8.dp))

                if (activeMeds.isEmpty()) {
                    Text("ยังไม่มียาที่กำลังใช้อยู่ในขณะนี้", color = Color.Gray)
                } else {
                    activeMeds.forEach { med ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(med.name, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                            if (med.dosage.isNotBlank()) {
                                Text("ขนาดยา: ${med.dosage}", color = Color.DarkGray, fontSize = 14.sp)
                            }
                            if (med.times.isNotEmpty()) {
                                Text("เวลา: ${med.times.joinToString(", ")}", color = Color.Gray, fontSize = 14.sp)
                            }
                            Text("คงเหลือ: ${med.quantityRemaining}", color = Color(0xFF33691E), fontSize = 13.sp)
                            Divider(Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // ออกจากระบบ
        Button(
            onClick = {
                auth.signOut()
                navController.navigate("login") {
                    popUpTo("profile") { inclusive = true }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
        ) { Text("ออกจากระบบ", color = Color.White, fontWeight = FontWeight.Bold) }

        // ลบบัญชี
        OutlinedButton(
            onClick = {
                try {
                    auth.currentUser?.delete()
                    navController.navigate("login") {
                        popUpTo("profile") { inclusive = true }
                    }
                } catch (_: Exception) { /* ใส่ Snackbar แจ้งเตือนภายหลังได้ */ }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2E7D32))
        ) { Text("ลบบัญชีนี้") }

        Spacer(Modifier.height(12.dp))
    }
}

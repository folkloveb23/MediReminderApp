package com.example.medireminderapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(navController: NavController) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun ensureUserDocument(uid: String, name: String, emailAddr: String) {
        val data = mapOf(
            "name" to name,
            "email" to emailAddr,
            "createdAt" to FieldValue.serverTimestamp(),
            "lastLoginAt" to FieldValue.serverTimestamp()
        )
        db.collection("users").document(uid).set(data, SetOptions.merge())
    }

    // 🟢 พื้นหลังไล่เฉดเขียวพาสเทล
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFA8E6A3), Color(0xFF8ED1A5))
                )
            )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("ลงทะเบียน", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.Black
                    )
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->

            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = "สร้างบัญชีใหม่",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B5E20),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // 🧩 กล่องข้อมูลโค้งมนสีขาว
                Card(
                    shape = RoundedCornerShape(30.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("ชื่อผู้ใช้") },
                            singleLine = true,
                            shape = RoundedCornerShape(50.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = Color(0xFF43A047),
                                unfocusedBorderColor = Color(0xFF81C784)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("อีเมล") },
                            singleLine = true,
                            shape = RoundedCornerShape(50.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = Color(0xFF43A047),
                                unfocusedBorderColor = Color(0xFF81C784)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // ช่องรหัสผ่าน (ถูกต้องตาม label แล้ว)
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("รหัสผ่าน") },
                            singleLine = true,
                            shape = RoundedCornerShape(50.dp),
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                val icon = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(icon, contentDescription = null)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = Color(0xFF43A047),
                                unfocusedBorderColor = Color(0xFF81C784)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // ช่องยืนยันรหัสผ่าน (ถูกต้องตาม label แล้ว)
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("ยืนยันรหัสผ่าน") },
                            singleLine = true,
                            shape = RoundedCornerShape(50.dp),
                            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                val icon = if (confirmPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                    Icon(icon, contentDescription = null)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = Color(0xFF43A047),
                                unfocusedBorderColor = Color(0xFF81C784)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ปุ่มลงทะเบียน
                Button(
                    onClick = {
                        errorMessage = null
                        val nameTrim = username.trim()
                        val emailTrim = email.trim().lowercase()

                        when {
                            nameTrim.isEmpty() || emailTrim.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() ->
                                errorMessage = "กรุณากรอกข้อมูลให้ครบ"

                            password.length < 6 ->
                                errorMessage = "รหัสผ่านต้องมีอย่างน้อย 6 ตัวอักษร"

                            password != confirmPassword ->
                                errorMessage = "รหัสผ่านกับยืนยันรหัสผ่านไม่ตรงกัน"

                            else -> {
                                loading = true
                                auth.createUserWithEmailAndPassword(emailTrim, password)
                                    .addOnCompleteListener { task ->
                                        loading = false
                                        if (task.isSuccessful) {
                                            val uid = auth.currentUser?.uid
                                            if (uid != null) {
                                                // เขียนเอกสาร users/{uid} (idempotent)
                                                ensureUserDocument(uid, nameTrim, emailTrim)
                                            }
                                            // กลับไปหน้า Login (หรือ dashboard ตาม flow)
                                            navController.popBackStack()
                                        } else {
                                            errorMessage = task.exception?.localizedMessage ?: "ลงทะเบียนไม่สำเร็จ"
                                        }
                                    }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    enabled = !loading
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        if (loading) "กำลังสร้างบัญชี..." else "ลงทะเบียน",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                errorMessage?.let {
                    Text(
                        text = it,
                        color = Color.Red,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

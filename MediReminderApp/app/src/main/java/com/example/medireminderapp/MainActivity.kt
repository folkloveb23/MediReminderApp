package com.example.medireminderapp

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.medireminderapp.ui.theme.MediReminderAppTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    companion object {
        private const val RC_POST_NOTIFICATIONS = 9001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ✅ สร้าง Notification Channel ไว้ก่อน (Android 8+)
        NotificationHelper.ensureChannel(this)

        // ✅ ขอสิทธิ "แจ้งเตือน" เฉพาะ Android 13+ (API 33)
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    RC_POST_NOTIFICATIONS
                )
            }
        }

        // ✅ ขอสิทธิ "ตั้งปลุกแบบ exact" เฉพาะ Android 12+ (API 31)
        if (Build.VERSION.SDK_INT >= 31) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                } catch (_: Exception) {
                    // บางรอมอาจไม่รองรับ action นี้ ข้ามได้
                }
            }
        }

        // ✅ ตั้งรีเฟรชเวลา 00:01 ของทุกวัน (ครั้งแรก)
        AlarmScheduler.scheduleMidnightRefresh(this)

        // ✅ ถ้ายังมี session อยู่ ให้ตั้งปลุกของ "วันนี้" ทันทีที่เข้าแอป
        FirebaseAuth.getInstance().currentUser?.let {
            AlarmScheduler.scheduleToday(this)
        }

        // ✅ Debug: โชว์โปรเจกต์ Firebase ที่เชื่อมอยู่
        try {
            val app = FirebaseApp.getInstance()
            val opt = app.options
            Log.d("FB_ACTIVE_PROJECT", """
🔥 Firebase Active Project:
  projectId = ${opt.projectId}
  appId     = ${opt.applicationId}
  apiKey    = ${opt.apiKey}
  bucket    = ${opt.storageBucket}
""".trimIndent())
        } catch (e: Exception) {
            Log.e("FB_ACTIVE_PROJECT", "❌ FirebaseApp ไม่พร้อม", e)
        }

        setContent {
            MediReminderAppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MediReminderApp()
                }
            }
        }
    }

    // ✅ รีเฟรชตั้งปลุกทุกครั้งที่แอพกลับมา foreground (ไม่ต้องล็อกอินใหม่)
    override fun onResume() {
        super.onResume()
        if (FirebaseAuth.getInstance().currentUser != null) {
            AlarmScheduler.scheduleToday(this)
        }
    }
}

// ✅ ฟังก์ชันทดสอบสิทธิ์ Firestore หลังล็อกอิน
@Composable
private fun FirestoreRulesSelfTest() {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    LaunchedEffect(auth.currentUser?.uid) {
        val uid = auth.currentUser?.uid ?: return@LaunchedEffect
        val todayKey = SimpleDateFormat("yyyyMMdd", Locale.US)
            .format(Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time)

        Log.d("FIREBASE_RULES_TEST", "🧪 เริ่มเทส /users/$uid/skips/$todayKey")

        db.collection("users").document(uid)
            .collection("skips").document(todayKey)
            .set(mapOf("medIds" to FieldValue.arrayUnion("_self_test_")), SetOptions.merge())
            .addOnSuccessListener {
                Log.d("FIREBASE_RULES_TEST", "✅ เขียน /users/$uid/skips/$todayKey สำเร็จ (Rules ผ่าน)")
            }
            .addOnFailureListener { e ->
                Log.e("FIREBASE_RULES_TEST", "❌ เขียน /users/$uid/skips/$todayKey ไม่ได้ (Rules ผิดพลาด)", e)
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediReminderApp() {
    FirestoreRulesSelfTest() // ✅ เรียกทดสอบอัตโนมัติหลังล็อกอิน

    val navController = rememberNavController()
    val vm: MedicinesViewModel = viewModel()
    val auth = FirebaseAuth.getInstance()
    val ctx = LocalContext.current

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = currentRoute in listOf("dashboard", "profile", "calendar")

    val barColor = Color(0xFFDFF3E1)
    val iconActive = Color(0xFF2F855A)
    val iconInactive = MaterialTheme.colorScheme.onSurfaceVariant
    val fabContainer = Color(0xFF37A169)
    val fabContent = Color.White

    Scaffold(
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            if (currentRoute in listOf("dashboard", "calendar")) {
                FloatingActionButton(
                    onClick = { navController.navigate("choose_add_method") },
                    containerColor = fabContainer,
                    contentColor = fabContent,
                    shape = CircleShape
                ) { Icon(Icons.Default.Add, contentDescription = "เพิ่มยา") }
            }
        },
        bottomBar = {
            if (showBar) {
                BottomAppBar(
                    containerColor = barColor,
                    tonalElevation = 3.dp,
                    modifier = Modifier.navigationBarsPadding(),
                    actions = {
                        val fabHole = 64.dp
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable {
                                            navController.navigate("dashboard") {
                                                launchSingleTop = true
                                                popUpTo("dashboard") { inclusive = false }
                                            }
                                        }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Home, contentDescription = "หน้าแรก",
                                        tint = if (currentRoute == "dashboard") iconActive else iconInactive
                                    )
                                    Text(
                                        "หน้าแรก",
                                        color = if (currentRoute == "dashboard") iconActive else iconInactive,
                                        fontSize = 12.sp
                                    )
                                }

                                Spacer(Modifier.width(fabHole))

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable {
                                            navController.navigate("profile") {
                                                launchSingleTop = true
                                                popUpTo("dashboard") { inclusive = false }
                                            }
                                        }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Person, contentDescription = "โปรไฟล์",
                                        tint = if (currentRoute == "profile") iconActive else iconInactive
                                    )
                                    Text(
                                        "โปรไฟล์",
                                        color = if (currentRoute == "profile") iconActive else iconInactive,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "splash",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("splash") {
                LaunchedEffect(true) {
                    if (auth.currentUser != null) {
                        navController.navigate("dashboard") { popUpTo("splash") { inclusive = true } }
                    } else {
                        navController.navigate("login") { popUpTo("splash") { inclusive = true } }
                    }
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }

            composable("login") {
                LoginScreen(navController = navController, onLoginSuccess = {
                    navController.navigate("dashboard") { popUpTo("login") { inclusive = true } }
                    // ✅ ตั้งปลุกของ "วันนี้" และตั้งรีเฟรชวันถัดไป
                    AlarmScheduler.scheduleToday(ctx)
                    AlarmScheduler.scheduleMidnightRefresh(ctx)
                })
            }
            composable("register") { RegistrationScreen(navController) }
            composable("dashboard") { DashboardScreen(navController, vm) }
            composable("calendar") { CalendarMonthScreen() }
            composable("profile") { ProfileScreen(navController, vm) }

            composable("edit/{medId}") { entry ->
                val id = entry.arguments?.getString("medId")
                if (id.isNullOrBlank()) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    EditMedicineScreen(navController = navController, medId = id)
                }
            }

            composable("choose_add_method") {
                ChooseMethodScreen(
                    onAddManually = { navController.navigate("add_medicine") },
                    onScanPhoto = { navController.navigate("ocr_scan") },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("add_medicine") {
                AddMedicineScreen(navController, vm, onDismiss = { navController.popBackStack() })
            }
            composable("ocr_scan") { OcrScanScreen(navController = navController) }
        }
    }
}

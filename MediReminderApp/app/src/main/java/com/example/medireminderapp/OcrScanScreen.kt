package com.example.medireminderapp

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScanScreen(navController: NavHostController) {
    val context = LocalContext.current
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> imageUri = uri; error = null }

    // ---------------- helpers: เลือกบรรทัดที่ดูเหมือนชื่อยา ----------------
    fun upperRatio(s: String): Double {
        val letters = s.count { it.isLetter() }
        if (letters == 0) return 0.0
        val upp = s.count { it.isUpperCase() }
        return upp.toDouble() / letters
    }
    fun lineScore(line: Text.Line): Double {
        val text = line.text.trim()
        if (text.isBlank()) return -1.0
        val bbH = line.boundingBox?.height() ?: 0
        val letters = text.count { it.isLetter() }
        val digits  = text.count { it.isDigit() }
        val upr     = upperRatio(text)
        return bbH * 1.0 + letters * 2.0 + upr * 40.0 - digits * 1.5
    }

    // ---------------- form-decider: เม็ด/น้ำ + ค่าที่ต้องใช้ ----------------
    data class ParsedForm(
        val stockUnit: String,     // "ML" | "PILL"
        val volumeMl: String = "", // mL ทั้งขวด (ถ้าเป็นน้ำ)
        val perDoseMl: String = "",// mL ต่อครั้ง (ถ้าเป็นน้ำ)
        val pillQty: String = ""   // จำนวนเม็ด (ถ้าเป็นเม็ด)
    )

    fun parseLiquidNumbers(t: String): Pair<String, String> {
        val text = t.lowercase()

        // ปริมาตรรวม: หา label ก่อน ถ้าไม่เจอใช้ค่า mL ที่มากสุด โดยกันเคส mg/5 mL
        val volFromLabel = Regex("""(ขนาดบรรจุ|ปริมาตร|บรรจุ)\s*(\d{2,4}(?:\.\d+)?)\s*(m?l|ml|มล|มิลลิลิตร)""")
            .find(text)?.groupValues?.getOrNull(2)

        val allMlCandidates = Regex("""\b(\d{2,4}(?:\.\d+)?)\s*(m?l|ml|มล|มิลลิลิตร)\b""")
            .findAll(text)
            .mapNotNull { m ->
                val around = text.substring(max(0, m.range.first - 12), minOf(text.length, m.range.last + 12))
                if (around.contains("mg/")) null else m.groupValues[1].toDoubleOrNull()
            }.toList()

        val volume = volFromLabel
            ?: (allMlCandidates.maxOrNull()?.toInt()?.toString() ?: "")

        // ปริมาณต่อครั้ง: ครั้งละ/ต่อครั้ง ... mL, หรือ ... mL / dose, หรือช้อนชา/ช้อนโต๊ะ
        val perDoseDirect = Regex("""(ครั้งละ|ต่อครั้ง)\s*(\d{1,3}(?:\.\d+)?)\s*(m?l|ml|มล|มิลลิลิตร)\b""")
            .find(text)?.groupValues?.getOrNull(2)
            ?: Regex("""\b(\d{1,3}(?:\.\d+)?)\s*(m?l|ml|มล|มิลลิลิตร)\b\s*(/|per)\s*(dose|time)\b""")
                .find(text)?.groupValues?.getOrNull(1)

        val tsp = Regex("""(\d+(?:\.\d+)?)\s*ช้อนชา""").find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let {
            (it * 5.0).roundToInt().toString()
        }
        val tbsp = Regex("""(\d+(?:\.\d+)?)\s*ช้อนโต๊ะ""").find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let {
            (it * 15.0).roundToInt().toString()
        }

        val perDose = perDoseDirect ?: tsp ?: tbsp ?: ""
        return volume to perDose
    }

    fun parsePillQty(t: String): String {
        val text = t.lowercase()
        Regex("""\b(\d{1,3})\s*(เม็ด|tabs?|tablets?)\b""").find(text)?.let { return it.groupValues[1] }
        Regex("""[x×]\s*(\d{1,3})\b""").find(text)?.let { return it.groupValues[1] }
        return ""
    }

    fun decideFormFromOcr(fullText: String): ParsedForm {
        val t = fullText.lowercase()
        val hasLiquidWord = listOf(
            "syrup","solution","suspension","elixir","drops","oral liquid","liquid",
            "ยาน้ำ","น้ำเชื่อม","หยด","ละลาย"
        ).any { it in t }
        val hasMl = Regex("""\b\d{1,4}\s*(m?l|ml|มล|มิลลิลิตร)\b""").containsMatchIn(t)
        val hasPerDose = Regex("""(ครั้งละ|ต่อครั้ง|/dose|per\s*dose)""").containsMatchIn(t)

        return if (hasLiquidWord || (hasMl && hasPerDose)) {
            val (vol, per) = parseLiquidNumbers(t)
            ParsedForm(stockUnit = "ML", volumeMl = vol, perDoseMl = per)
        } else {
            val qty = parsePillQty(t)
            ParsedForm(stockUnit = "PILL", pillQty = qty)
        }
    }

    fun processImage(uri: Uri) {
        busy = true
        error = null
        try {
            val image = InputImage.fromFilePath(context, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    busy = false

                    // 1) จัดข้อความ: เอาบรรทัดที่เด่นสุดวางไว้หัว + ตามด้วยทั้งก้อน
                    val lines = visionText.textBlocks.flatMap { it.lines }
                    val best = lines.maxByOrNull { lineScore(it) }?.text?.trim().orEmpty()
                    val orderedText = buildString {
                        if (best.isNotBlank()) append(best).append('\n')
                        append(visionText.text)
                    }.trim()
                    if (orderedText.isBlank()) { error = "ไม่พบข้อความในภาพ"; return@addOnSuccessListener }

                    // 2) ชื่อ/ความแรง (ใช้ OcrParser ของโปรเจ็กต์)
                    val r0 = OcrParser.parse(orderedText)
                    val r = if (r0.name.isBlank() && best.isNotBlank()) OcrParser.parse(best) else r0

                    // 3) ตัดสินเม็ด/น้ำ + ดึงค่าที่ต้องใช้
                    val form = decideFormFromOcr(orderedText)

                    // 4) ส่งค่ากลับไปหน้า add_medicine
                    var addEntry = try { navController.getBackStackEntry("add_medicine") }
                    catch (_: IllegalArgumentException) { null }
                    if (addEntry == null) {
                        navController.navigate("add_medicine") { launchSingleTop = true }
                        addEntry = navController.getBackStackEntry("add_medicine")
                    }

                    addEntry.savedStateHandle.apply {
                        // ชื่อ/ความแรง
                        set("ocr_name", r.name)
                        set("ocr_strength_value", r.strengthValue)
                        set("ocr_strength_unit", r.strengthUnit)

                        // ประเภท + ค่าที่เกี่ยวข้อง
                        set("ocr_stock_unit", form.stockUnit)
                        if (form.stockUnit == "ML") {
                            if (form.volumeMl.isNotBlank()) set("ocr_bottle_volume_ml", form.volumeMl)
                            if (form.perDoseMl.isNotBlank()) set("ocr_dose_per_intake_ml", form.perDoseMl)
                            set("ocr_refill_threshold_doses", "4") // ดีฟอลต์
                            // ไม่ต้อง set ocr_quantity; หน้า Add จะคำนวณ “ครั้งที่ใช้ได้” เอง
                        } else {
                            // เม็ด
                            val qty = if (form.pillQty.isNotBlank()) form.pillQty else r.quantity
                            if (qty.isNotBlank()) set("ocr_quantity", qty)
                        }
                    }

                    navController.popBackStack("add_medicine", false)
                }
                .addOnFailureListener { e ->
                    busy = false
                    error = "สแกนล้มเหลว: ${e.message}"
                    Log.e("OcrDebug", "Text recognition failed", e)
                }
        } catch (_: IOException) {
            busy = false
            error = "ไม่สามารถโหลดรูปภาพได้"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("สแกนฉลากยา") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = { pickImage.launch("image/*") }) { Text("เลือกภาพจากแกลเลอรี") }
            Spacer(Modifier.height(12.dp))
            AsyncImage(
                model = imageUri,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(220.dp)
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { imageUri?.let { processImage(it) } ?: run { error = "กรุณาเลือกภาพก่อน" } },
                enabled = !busy
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 3.dp,
                        color = Color.White
                    )
                } else Text("สแกนจากรูป")
            }
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

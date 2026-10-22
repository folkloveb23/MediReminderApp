package com.example.medireminderapp

data class OcrParseResult(
    val name: String = "",
    val strengthValue: String = "",
    val strengthUnit: String = "",
    val quantity: String = ""
)

object OcrParser {

    // พจนานุกรม + canonical mapping (แบรนด์ → ชื่อสามัญ)
    private val DRUG_LEXICON = listOf(
        // Generic
        "Paracetamol","Acetaminophen","Ibuprofen","Amoxicillin","Azithromycin","Ciprofloxacin",
        "Clarithromycin","Omeprazole","Lansoprazole","Atorvastatin","Simvastatin","Metformin",
        "Aspirin","Cetirizine","Loratadine","Diphenhydramine","Dextromethorphan","Guaifenesin",
        "Bromhexine","Salbutamol","Prednisolone","Acetylcysteine","Naproxen","Diclofenac",
        // Brands
        "Paracetamol GPO","Tylenol","Panadol","Nurofen","Mucinex","Zyrtec","Claritine",
        "Tiffy","Decolgen","Silomat","Actifed","Ponstan","Bisolvon","Mucosolvan","Benadryl","Coldrex"
    )

    // map ชื่อแบรนด์ → ชื่อสามัญ (ปรับเพิ่มได้)
    private val BRAND_TO_GENERIC = mapOf(
        "Tylenol" to "Paracetamol",
        "Panadol" to "Paracetamol",
        "Paracetamol GPO" to "Paracetamol",
        "Nurofen" to "Ibuprofen",
        "Zyrtec" to "Cetirizine",
        "Claritine" to "Loratadine",
        "Tiffy" to "Paracetamol",
        "Decolgen" to "Paracetamol",
        "Actifed" to "Diphenhydramine",
        "Benadryl" to "Diphenhydramine",
        "Coldrex" to "Paracetamol",
        "Bisolvon" to "Bromhexine",
        "Mucosolvan" to "Ambroxol" // ถ้าต้องการใส่ Ambroxol ใน lexicon ด้วย
    )

    // คำส่วนท้ายที่ไม่ใช่ชื่อยา ให้ตัดทิ้ง
    private val TAIL_WORDS = setOf(
        "tablet","tablets","tab","tabs","capsule","capsules","cap","caps",
        "syrup","suspension","solution","drops","cream","ointment","gel",
        "bp","usp","ip","gpo","mg","mcg","g","ml","iu","%","oral","film","coated"
    )

    /* -------------------- Normalization helpers -------------------- */

    private fun normalizeForName(raw: String): String {
        val s = raw
            .replace('0','O').replace('1','I').replace('|','I')
            .replace('5','S').replace('8','B').replace('6','G')
            .replace(Regex("[^A-Za-z\\s-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val words = s.split(" ")
            .filter { it.isNotBlank() }
            .takeWhile { it.lowercase() !in TAIL_WORDS }

        return words.joinToString(" ") { w ->
            w.substring(0,1).uppercase() + w.substring(1).lowercase()
        }.trim()
    }

    private fun extractStrength(text: String): Pair<String,String> {
        val re = Regex("""(?i)\b(\d+(?:\.\d+)?)\s*(mg|mcg|g|ml|mL|IU|%)\b""")
        val m  = re.find(text) ?: return "" to ""
        val v = m.groupValues[1]
        var u = m.groupValues[2]
        if (u == "mL") u = "ml"
        return v to u.lowercase()
    }

    private fun extractQuantity(text: String): String {
        val re = Regex("""(?i)\b(\d{1,3})\s*(tabs?|tablets?|caps?|cap|เม็ด|pcs?)\b|x\s?(\d{1,3})\b""")
        val m = re.find(text) ?: return ""
        return when {
            m.groupValues.getOrNull(1)?.isNotBlank() == true -> m.groupValues[1]
            m.groupValues.getOrNull(3)?.isNotBlank() == true -> m.groupValues[3]
            else -> ""
        }
    }

    /* -------------------- Similarity (Jaro-Winkler) -------------------- */

    private fun jaroWinkler(a: String, b: String): Double {
        val s1 = a.lowercase()
        val s2 = b.lowercase()
        if (s1 == s2) return 1.0
        val matchDistance = (maxOf(s1.length, s2.length) / 2) - 1
        val s1Matches = BooleanArray(s1.length)
        val s2Matches = BooleanArray(s2.length)
        var matches = 0
        var transpositions = 0

        for (i in s1.indices) {
            val start = maxOf(0, i - matchDistance)
            val end = minOf(i + matchDistance + 1, s2.length)
            for (j in start until end) {
                if (s2Matches[j]) continue
                if (s1[i] != s2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }
        if (matches == 0) return 0.0

        var k = 0
        for (i in s1.indices) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }
        val m = matches.toDouble()
        val jaro = ((m / s1.length) + (m / s2.length) + ((m - transpositions / 2.0) / m)) / 3.0

        // Prefix boost
        var prefix = 0
        for (i in 0 until minOf(4, minOf(s1.length, s2.length))) {
            if (s1[i] == s2[i]) prefix++ else break
        }
        return jaro + prefix * 0.1 * (1 - jaro)
    }

    /* -------------------- Candidate generation (n-gram) -------------------- */

    private fun bestDrugFromWholeText(full: String): String {
        // เอา 8 บรรทัดบนสุดพอ ลด noise
        val lines = full.lines().map { it.trim() }.filter { it.isNotBlank() }.take(8)

        val tokens = lines
            .flatMap { it.split(Regex("\\s+")) }
            .map { it.replace(Regex("[^A-Za-z-]"), "") }
            .filter { it.length >= 3 }

        // สร้าง n-grams 1–3 คำ
        val grams = mutableListOf<String>()
        for (i in tokens.indices) {
            grams.add(tokens[i])
            if (i + 1 < tokens.size) grams.add(tokens[i] + " " + tokens[i + 1])
            if (i + 2 < tokens.size) grams.add(tokens[i] + " " + tokens[i + 1] + " " + tokens[i + 2])
        }

        val candidates = grams
            .map(::normalizeForName)
            .filter { it.isNotBlank() }
            .distinct()

        var best = ""
        var bestScore = 0.0
        for (cand in candidates) {
            for (lex in DRUG_LEXICON) {
                val s = jaroWinkler(cand, lex)
                if (s > bestScore) {
                    bestScore = s
                    best = lex
                }
            }
        }

        // เกณฑ์รับค่อนข้างโหดเพื่อกันเดาผิดหนัก ๆ
        if (bestScore >= 0.82) {
            // ถ้าเป็นแบรนด์ map ไปชื่อสามัญ
            return BRAND_TO_GENERIC[best] ?: best
        }

        // ไม่ถึงเกณฑ์ → ใช้ candidate แรกที่ normalize แล้ว (กัน empty)
        return candidates.firstOrNull().orEmpty()
    }

    /* -------------------- Public parse -------------------- */

    fun parse(fullText: String): OcrParseResult {
        if (fullText.isBlank()) return OcrParseResult()

        val (sv, su) = extractStrength(fullText)
        val qty = extractQuantity(fullText)

        val name = bestDrugFromWholeText(fullText)

        return OcrParseResult(
            name = name,
            strengthValue = sv,
            strengthUnit = su,
            quantity = qty
        )
    }
}

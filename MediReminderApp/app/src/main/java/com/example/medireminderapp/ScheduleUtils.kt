package com.example.medireminderapp

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/**
 * ยูทิลิตี้สำหรับคำนวณการนัดกินยา / ตรวจสอบว่ายานี้ควรกินในวันนั้นไหม
 * (เวอร์ชันดั้งเดิม: ยังไม่มีระบบแจ้งเตือน AlarmManager)
 */
object ScheduleUtils {

    /**
     * ตรวจสอบว่ายานี้ควรรับประทานในวัน date หรือไม่
     */
    fun qualifiesByFrequency(frequency: String, daysOfWeek: List<String>, date: LocalDate): Boolean {
        val freq = frequency.lowercase(Locale.ROOT)
        return when {
            freq.startsWith("ทุกวัน") -> true
            freq.startsWith("วันคู่") -> date.dayOfMonth % 2 == 0
            freq.startsWith("วันคี่") -> date.dayOfMonth % 2 == 1
            freq.startsWith("เลือกวัน") -> {
                val map = mapOf(
                    DayOfWeek.SUNDAY to "อา.",
                    DayOfWeek.MONDAY to "จ.",
                    DayOfWeek.TUESDAY to "อ.",
                    DayOfWeek.WEDNESDAY to "พ.",
                    DayOfWeek.THURSDAY to "พฤ.",
                    DayOfWeek.FRIDAY to "ศ.",
                    DayOfWeek.SATURDAY to "ส."
                )
                daysOfWeek.contains(map[date.dayOfWeek])
            }
            else -> true
        }
    }

    /**
     * คืนค่ารายชื่อวันในสัปดาห์ (ไทย) ที่ใช้กับยา
     */
    fun thaiDaysOfWeek(): List<String> = listOf("อา.", "จ.", "อ.", "พ.", "พฤ.", "ศ.", "ส.")

    /**
     * ใช้เช็กว่าช่วงวันของคอร์สยาหมดหรือยัง
     */
    fun isCourseStillActive(endDateMillis: Long?): Boolean {
        if (endDateMillis == null) return true
        val today = System.currentTimeMillis()
        return today <= endDateMillis
    }
}

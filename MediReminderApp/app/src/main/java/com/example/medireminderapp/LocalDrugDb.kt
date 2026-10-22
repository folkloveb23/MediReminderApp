package com.example.medireminderapp

object LocalDrugDb {
    private val barcodeMap = mapOf(
        "8851979300125" to "Paracetamol GPO",
        "4897036690017" to "Panadol",
        "8888021300094" to "Tylenol"
    )
    fun lookupByBarcode(code: String): String? = barcodeMap[code]
}

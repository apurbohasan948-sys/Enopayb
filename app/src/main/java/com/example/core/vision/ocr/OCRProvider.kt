package com.example.core.vision.ocr

import android.graphics.Bitmap
import android.graphics.Rect

data class OcrElement(
    val text: String,
    val boundingBox: Rect,
    val confidence: Float,
    val lineIndex: Int = 0
)

data class OcrResult(
    val elements: List<OcrElement>,
    val fullText: String,
    val executionTimeMs: Long
)

interface OCRProvider {
    val providerName: String
    suspend fun extractText(bitmap: Bitmap?): OcrResult
}

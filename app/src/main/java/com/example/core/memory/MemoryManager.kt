package com.example.core.memory

import android.util.Log
import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.MemoryCategory
import com.example.data.local.entity.MemoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * MemoryManager.
 * Central coordinator for JARVIS Long-Term Memory.
 * Manages User Preferences, Personal Context, Task History, App Patterns, and Corrections.
 * Enforces strict privacy filtering to prevent storing credentials, passwords, or tokens.
 */
class MemoryManager(
    private val dao: JarvisDao
) {
    companion object {
        private const val TAG = "JARVIS_MemoryManager"

        private val SENSITIVE_PATTERNS = listOf(
            Regex("(?i)password\\s*[:=]\\s*\\S+"),
            Regex("(?i)pin\\s*[:=]\\s*\\d+"),
            Regex("(?i)token\\s*[:=]\\s*[a-zA-Z0-9_-]+"),
            Regex("(?i)bearer\\s+[a-zA-Z0-9_.-]+"),
            Regex("(?i)otp\\s*[:=]?\\s*\\d{4,8}"),
            Regex("(?i)\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13})\\b"), // Credit cards
            Regex("(?i)secret_key\\s*[:=]\\s*\\S+")
        )
    }

    val allMemories: Flow<List<MemoryEntity>> = dao.getAllMemories()

    fun getMemoriesByCategory(category: MemoryCategory): Flow<List<MemoryEntity>> =
        dao.getMemoriesByCategory(category)

    /**
     * Stores or updates a long-term memory with safety and privacy validation.
     */
    suspend fun recordMemory(
        category: MemoryCategory,
        key: String,
        value: String,
        confidence: Float = 0.95f,
        importance: Float = 0.7f,
        source: String = "User Conversation"
    ): Long = withContext(Dispatchers.IO) {
        if (!isContentSafeForMemory(key) || !isContentSafeForMemory(value)) {
            Log.w(TAG, "Privacy Shield: Memory rejected due to sensitive data marker.")
            return@withContext -1L
        }

        val existing = dao.searchMemories(key).firstOrNull { it.key.equals(key, ignoreCase = true) }
        return@withContext if (existing != null) {
            val updated = existing.copy(
                category = category,
                value = value,
                confidence = maxOf(existing.confidence, confidence),
                importance = maxOf(existing.importance, importance),
                source = source,
                updatedAt = System.currentTimeMillis()
            )
            dao.updateMemory(updated)
            existing.id
        } else {
            val newMemory = MemoryEntity(
                category = category,
                key = key.trim(),
                value = value.trim(),
                confidence = confidence,
                source = source,
                importance = importance,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            dao.insertMemory(newMemory)
        }
    }

    /**
     * Privacy check: Filters out passwords, credit cards, auth tokens, and OTPs.
     */
    fun isContentSafeForMemory(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        if (lower.contains("password") || lower.contains("pin code") || lower.contains("credit card") || lower.contains("cvv")) {
            return false
        }
        for (pattern in SENSITIVE_PATTERNS) {
            if (pattern.containsMatchIn(text)) {
                return false
            }
        }
        return true
    }

    suspend fun searchMemories(query: String): List<MemoryEntity> = withContext(Dispatchers.IO) {
        dao.searchMemories(query)
    }

    suspend fun deleteMemory(memory: MemoryEntity) = withContext(Dispatchers.IO) {
        dao.deleteMemory(memory)
    }

    suspend fun clearMemoriesByCategory(category: MemoryCategory) = withContext(Dispatchers.IO) {
        dao.clearMemoriesByCategory(category)
    }

    suspend fun clearAllMemories() = withContext(Dispatchers.IO) {
        dao.clearAllMemories()
    }
}

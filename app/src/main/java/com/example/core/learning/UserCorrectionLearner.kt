package com.example.core.learning

import android.util.Log
import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.UserCorrectionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * UserCorrectionLearner.
 * Captures explicit corrections made by the user when an action is wrong or sub-optimal.
 * Applies corrections dynamically to future screen resolving and planning.
 */
class UserCorrectionLearner(
    private val dao: JarvisDao
) {
    companion object {
        private const val TAG = "JARVIS_CorrectionLearner"
    }

    val allCorrections: Flow<List<UserCorrectionEntity>> = dao.getAllUserCorrections()

    /**
     * Records a new user correction with context.
     */
    suspend fun recordCorrection(
        userGoal: String,
        previousAssumption: String,
        userCorrection: String,
        correctedAction: String,
        actualTarget: String,
        appPackage: String,
        screenContext: String = "general"
    ): Long = withContext(Dispatchers.IO) {
        val entity = UserCorrectionEntity(
            userGoal = userGoal.trim(),
            previousAssumption = previousAssumption.trim(),
            userCorrection = userCorrection.trim(),
            correctedAction = correctedAction.trim(),
            actualTarget = actualTarget.trim(),
            appPackage = appPackage.ifBlank { "unknown" },
            screenContext = screenContext.ifBlank { "general" },
            confidence = 1.0f,
            timestamp = System.currentTimeMillis()
        )
        val id = dao.insertUserCorrection(entity)
        Log.d(TAG, "Recorded User Correction ID $id for '$appPackage' ($previousAssumption -> $actualTarget)")
        id
    }

    /**
     * Queries applicable corrections for the active application and screen.
     */
    suspend fun getCorrectionsForContext(pkg: String, screenContext: String): List<UserCorrectionEntity> = withContext(Dispatchers.IO) {
        dao.getCorrectionsForContext(pkg, screenContext)
    }

    suspend fun markCorrectionApplied(correctionId: Long) = withContext(Dispatchers.IO) {
        dao.incrementCorrectionApplied(correctionId)
    }

    suspend fun deleteCorrection(correction: UserCorrectionEntity) = withContext(Dispatchers.IO) {
        dao.deleteUserCorrection(correction)
    }

    suspend fun clearAllCorrections() = withContext(Dispatchers.IO) {
        dao.clearAllUserCorrections()
    }
}

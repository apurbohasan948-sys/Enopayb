package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DatasetFormat {
    ALPACA,
    SHAREGPT,
    JSONL_RAW
}

@Entity(tableName = "training_dataset")
data class TrainingExampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val inputInstruction: String,
    val contextSummary: String,
    val successfulPlanJson: String,
    val toolsUsedSummary: String,
    val verificationProof: String,
    val qualityScore: Float = 0.95f,
    val format: DatasetFormat = DatasetFormat.ALPACA,
    val isCurated: Boolean = true,
    val isExported: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

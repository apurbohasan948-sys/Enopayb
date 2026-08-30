package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "brain_snapshots")
data class BrainSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val snapshotVersion: String = "1.0",
    val createdAt: Long = System.currentTimeMillis(),
    val deviceProfile: String = "Redmi Note 12 / Android 15",
    val summaryJson: String = "{}",
    val exportedJson: String = "{}"
)

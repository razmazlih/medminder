package com.medminder.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pin_credential")
data class PinCredentialEntity(
    @PrimaryKey val id: Int = 1,
    val saltBase64: String,
    val hashBase64: String,
    val iterations: Int,
    val updatedAtEpochMs: Long
)

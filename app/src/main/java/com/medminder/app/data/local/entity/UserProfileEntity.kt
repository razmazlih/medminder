package com.medminder.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: String = "default",
    val ownerProfileId: String = "owner_primary",
    val displayName: String,
    val hourFormat24: Boolean,
    val seniorMode: Boolean,
    val language: String = "he",
    val timezoneMode: String = "DEVICE_AUTO"
)

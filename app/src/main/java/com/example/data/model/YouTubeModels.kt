package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "allowed_youtube_channels")
data class AllowedYouTubeChannelEntity(
    @PrimaryKey val channelId: String = UUID.randomUUID().toString(),
    val childId: String,
    val channelName: String,
    val handle: String = "",
    val description: String = "",
    val category: String = "Study & Focus",
    val subscriberCount: String = "",
    val videoCount: String = "",
    val avatarUrl: String = "",
    val channelUrl: String = "",
    val isAllowed: Boolean = true,
    val allowedAt: Long = System.currentTimeMillis()
)

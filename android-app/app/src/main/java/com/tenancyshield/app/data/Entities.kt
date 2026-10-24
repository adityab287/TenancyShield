package com.tenancyshield.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inspections")
data class Inspection(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val status: String, // "Pending" or "Locked"
    val finalPdfPath: String?
)


@Entity(tableName = "devices")
data class Device(
    @PrimaryKey val id: String, // could be an installation ID or simply "local"
    val certificateChain: String?,
    val playIntegrityToken: String?,
    val isVerified: Boolean?
)

@Entity(tableName = "anchors")
data class Anchor(
    @PrimaryKey val anchorId: String,
    val inspectionId: String,
    val chainHeadHash: String,
    val seq: Int,
    val method: String,
    val proofBlob: String?, // Base64 encoded TSA response
    val status: String // PENDING, CONFIRMED, FAILED
)

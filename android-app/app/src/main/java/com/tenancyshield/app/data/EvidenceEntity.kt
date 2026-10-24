package com.tenancyshield.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "evidence_table")
data class EvidenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sequenceNumber: Int,
    val itemId: String, // Strictly: "wall_1", "flooring", "ceiling", "meters"
    val auditPhase: String, // Strictly: "MOVE_IN" or "MOVE_OUT"
    val localImageUri: String,
    val rawHash: String,
    val previousChainHash: String,
    val chainHash: String,
    val signature: String,
    val tsaToken: String,
    val timestamp: Long,
    val baselineHash: String? = null,
    val inspectionId: String = "INSP_001",
    val signingKeyId: String? = "tenancyshield_ledger_key",
    val sigAlg: String? = "SHA256withECDSA"
) {
    // Backward compatibility aliases
    val itemName: String get() = itemId
    val fileHash: String get() = rawHash
    val previousHash: String? get() = previousChainHash.ifEmpty { null }
    val recordHash: String get() = chainHash
    val baselineEvidenceId: String? get() = baselineHash
}

typealias Evidence = EvidenceEntity

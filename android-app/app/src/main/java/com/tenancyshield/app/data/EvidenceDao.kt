package com.tenancyshield.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EvidenceDao {
    @Query("SELECT * FROM evidence_table WHERE itemId = :itemId AND auditPhase = :phase ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestByItemAndPhase(itemId: String, phase: String): EvidenceEntity?

    @Query("SELECT * FROM evidence_table ORDER BY sequenceNumber DESC LIMIT 1")
    suspend fun getLastEvidenceRecord(): EvidenceEntity?

    @Query("SELECT * FROM evidence_table WHERE itemId IN ('wall_1', 'flooring', 'ceiling', 'meters') ORDER BY sequenceNumber ASC")
    suspend fun getAllValidChecklistEvidence(): List<EvidenceEntity>

    @Query("DELETE FROM evidence_table WHERE itemId NOT IN ('wall_1', 'flooring', 'ceiling', 'meters')")
    suspend fun deleteLegacyTestRecords()

    @Query("DELETE FROM evidence_table")
    suspend fun clearAllEvidence()

    // Backward compatibility queries
    @Query("SELECT * FROM evidence_table WHERE inspectionId = :inspectionId ORDER BY sequenceNumber ASC")
    suspend fun getEvidenceForInspection(inspectionId: String): List<EvidenceEntity>

    @Query("SELECT * FROM evidence_table ORDER BY sequenceNumber DESC LIMIT 1")
    suspend fun getLastEvidence(): EvidenceEntity?

    @Query("SELECT * FROM evidence_table WHERE itemId = :itemName ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestEvidenceForItem(itemName: String): EvidenceEntity?

    @Query("SELECT * FROM evidence_table ORDER BY sequenceNumber ASC")
    suspend fun getAllEvidence(): List<EvidenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvidence(evidence: EvidenceEntity): Long
}

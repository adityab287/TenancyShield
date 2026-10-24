package com.tenancyshield.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InspectionDao {
    @Query("SELECT * FROM inspections ORDER BY timestamp DESC")
    fun getAllInspections(): Flow<List<Inspection>>

    @Insert
    suspend fun insertInspection(inspection: Inspection)
}


@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices WHERE id = :id LIMIT 1")
    suspend fun getDevice(id: String): Device?
    
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: Device)
}

@Dao
interface AnchorDao {
    @Query("SELECT * FROM anchors WHERE inspectionId = :inspectionId")
    suspend fun getAnchorsForInspection(inspectionId: String): List<Anchor>

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertAnchor(anchor: Anchor)
    
    @Update
    suspend fun updateAnchor(anchor: Anchor)
}

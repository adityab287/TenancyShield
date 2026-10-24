package com.tenancyshield.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE evidence ADD COLUMN signature TEXT")
        database.execSQL("ALTER TABLE evidence ADD COLUMN signingKeyId TEXT")
        database.execSQL("ALTER TABLE evidence ADD COLUMN sigAlg TEXT")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `devices` (`id` TEXT NOT NULL, `certificateChain` TEXT, `playIntegrityToken` TEXT, `isVerified` INTEGER, PRIMARY KEY(`id`))")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `anchors` (`anchorId` TEXT NOT NULL, `inspectionId` TEXT NOT NULL, `chainHeadHash` TEXT NOT NULL, `seq` INTEGER NOT NULL, `method` TEXT NOT NULL, `proofBlob` TEXT, `status` TEXT NOT NULL, PRIMARY KEY(`anchorId`))")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE evidence ADD COLUMN baselineEvidenceId TEXT")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE evidence ADD COLUMN auditPhase TEXT")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `evidence_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `inspectionId` TEXT NOT NULL,
                `itemName` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `fileHash` TEXT NOT NULL,
                `previousHash` TEXT,
                `recordHash` TEXT NOT NULL,
                `sequenceNumber` INTEGER NOT NULL,
                `signature` TEXT,
                `signingKeyId` TEXT,
                `sigAlg` TEXT,
                `baselineEvidenceId` TEXT,
                `auditPhase` TEXT NOT NULL DEFAULT 'MOVE_IN'
            )
        """.trimIndent())
        
        database.execSQL("""
            INSERT INTO `evidence_new` (`inspectionId`, `itemName`, `timestamp`, `fileHash`, `previousHash`, `recordHash`, `sequenceNumber`, `signature`, `signingKeyId`, `sigAlg`, `baselineEvidenceId`, `auditPhase`)
            SELECT `inspectionId`, `itemName`, `timestamp`, `fileHash`, `previousHash`, `recordHash`, `sequenceNumber`, `signature`, `signingKeyId`, `sigAlg`, `baselineEvidenceId`, COALESCE(`auditPhase`, 'MOVE_IN')
            FROM `evidence`
        """.trimIndent())
        
        database.execSQL("DROP TABLE `evidence`")
        database.execSQL("ALTER TABLE `evidence_new` RENAME TO `evidence`")
    }
}

@Database(entities = [Inspection::class, EvidenceEntity::class, Device::class, Anchor::class], version = 8, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun inspectionDao(): InspectionDao
    abstract fun evidenceDao(): EvidenceDao
    abstract fun deviceDao(): DeviceDao
    abstract fun anchorDao(): AnchorDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tenancyshield-db"
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

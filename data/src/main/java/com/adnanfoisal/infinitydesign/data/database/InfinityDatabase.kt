package com.adnanfoisal.infinitydesign.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context

/**
 * Single Room database for the app. Section 31: project metadata + serialized
 * DesignDocument blob, not hundreds of columns per property.
 *
 * Section 33: handle missing db, migration failure, corrupted records,
 * storage full — show recoverable error, never crash.
 */
@Database(
    entities = [ProjectEntity::class, BlueprintCacheEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(DbTypeConverters::class)
abstract class InfinityDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun blueprintDao(): BlueprintCacheDao

    companion object {
        const val NAME = "infinity-design.db"

        fun create(context: Context): InfinityDatabase =
            Room.databaseBuilder(context.applicationContext, InfinityDatabase::class.java, NAME)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}

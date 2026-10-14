package com.sunshine.app.search

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

/**
 * A saved place (design D5 of add-place-search): one row per OpenStreetMap object, replaced when
 * Photon returns it again. [matchText] is the title normalized for matching (design D7).
 */
@Entity(tableName = "place")
data class PlaceRow(
    @PrimaryKey val key: String,
    val title: String,
    val matchText: String,
    val label: String,
    val place: String?,
    val region: String?,
    val countryCode: String?,
    val latitude: Double,
    val longitude: Double,
    val west: Double?,
    val north: Double?,
    val east: Double?,
    val south: Double?,
    val receivedAt: Long,
)

/** A saved answer: the keys of its places in Photon's order, comma-separated (OSM keys hold no comma). */
@Entity(tableName = "answer", primaryKeys = ["text", "latitudeTenths", "longitudeTenths"])
data class AnswerRow(
    val text: String,
    val latitudeTenths: Int,
    val longitudeTenths: Int,
    val placeKeys: String,
    val receivedAt: Long,
)

@Dao
abstract class PlacesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertPlaces(places: List<PlaceRow>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAnswer(answer: AnswerRow)

    /** Stores an answer and its places together. */
    @Transaction
    open suspend fun save(
        answer: AnswerRow,
        places: List<PlaceRow>,
    ) {
        upsertPlaces(places)
        upsertAnswer(answer)
    }

    @Query("SELECT * FROM answer WHERE text = :text AND latitudeTenths = :latitudeTenths AND longitudeTenths = :longitudeTenths")
    abstract suspend fun answer(
        text: String,
        latitudeTenths: Int,
        longitudeTenths: Int,
    ): AnswerRow?

    @Query("SELECT * FROM place WHERE `key` IN (:keys)")
    abstract suspend fun places(keys: List<String>): List<PlaceRow>

    /** Places whose match text contains [word]; the word rule is applied by the caller. */
    @Query("SELECT * FROM place WHERE matchText LIKE '%' || :word || '%'")
    abstract suspend fun candidates(word: String): List<PlaceRow>

    @Query("DELETE FROM place")
    abstract suspend fun deletePlaces()

    @Query("DELETE FROM answer")
    abstract suspend fun deleteAnswers()

    /** Removes every saved place and answer together. */
    @Transaction
    open suspend fun clear() {
        deletePlaces()
        deleteAnswers()
    }
}

@Database(entities = [PlaceRow::class, AnswerRow::class], version = 1, exportSchema = false)
abstract class PlacesDatabase : RoomDatabase() {
    abstract fun dao(): PlacesDao

    companion object {
        /** Explicit, because `AUTOMATIC` asks the Context, which JVM tests do not have (as `OfflineDatabase`). */
        val JOURNAL_MODE = JournalMode.WRITE_AHEAD_LOGGING

        fun create(context: Context): PlacesDatabase =
            Room
                .databaseBuilder(context, PlacesDatabase::class.java, "places.db")
                .setJournalMode(JOURNAL_MODE)
                .build()
    }
}

package com.sunshine.app.di

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sunshine.app.data.connectivity.ConnectivityObserver
import com.sunshine.app.data.local.database.SunshineDatabase
import com.sunshine.app.data.remote.elevation.ElevationApi
import com.sunshine.app.data.repository.DefaultRegionProvider
import com.sunshine.app.data.repository.ElevationRepositoryImpl
import com.sunshine.app.data.repository.SettingsRepositoryImpl
import com.sunshine.app.data.repository.TileDownloadRepositoryImpl
import com.sunshine.app.domain.repository.ElevationRepository
import com.sunshine.app.domain.repository.RegionProvider
import com.sunshine.app.domain.repository.SettingsRepository
import com.sunshine.app.domain.repository.TileDownloadRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private val migrationSql =
    """
    CREATE TABLE IF NOT EXISTS downloaded_regions (
        regionId TEXT NOT NULL PRIMARY KEY,
        name TEXT NOT NULL,
        north REAL NOT NULL,
        south REAL NOT NULL,
        east REAL NOT NULL,
        west REAL NOT NULL,
        minZoom INTEGER NOT NULL,
        maxZoom INTEGER NOT NULL,
        totalTiles INTEGER NOT NULL,
        downloadedTiles INTEGER NOT NULL,
        sizeBytes INTEGER NOT NULL,
        downloadedAt INTEGER NOT NULL,
        status TEXT NOT NULL
    )
    """.trimIndent()

/**
 * Migration from version 1 to 2: adds downloaded_regions table.
 */
private val MIGRATION_1_2 = createMigration1To2()

private fun createMigration1To2(): Migration =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(migrationSql)
        }
    }

/**
 * Koin module for data layer dependencies (repositories, data sources).
 */
val dataModule =
    module {
        // Room Database
        single {
            Room.databaseBuilder(
                androidContext(),
                SunshineDatabase::class.java,
                SunshineDatabase.DATABASE_NAME,
            )
                .addMigrations(MIGRATION_1_2)
                .build()
        }

        // DAOs
        single { get<SunshineDatabase>().elevationDao() }
        single { get<SunshineDatabase>().downloadedRegionDao() }

        // HTTP Client for API calls
        single {
            HttpClient(Android) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }
            }
        }

        // Elevation API
        single { ElevationApi(httpClient = get()) }

        // Connectivity Observer
        single { ConnectivityObserver(context = androidContext()) }

        // Region Provider
        single<RegionProvider> { DefaultRegionProvider() }

        // Repositories
        single<SettingsRepository> { SettingsRepositoryImpl(context = androidContext()) }
        single<ElevationRepository> {
            ElevationRepositoryImpl(
                elevationDao = get(),
                elevationApi = get(),
                settingsRepository = get(),
            )
        }
        single<TileDownloadRepository> {
            TileDownloadRepositoryImpl(
                context = androidContext(),
                downloadedRegionDao = get(),
            )
        }
    }

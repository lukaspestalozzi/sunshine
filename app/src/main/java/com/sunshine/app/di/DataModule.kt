package com.sunshine.app.di

import androidx.room.Room
import com.sunshine.app.data.local.database.SunshineDatabase
import com.sunshine.app.data.remote.elevation.ElevationApi
import com.sunshine.app.data.repository.ElevationRepositoryImpl
import com.sunshine.app.data.repository.SettingsRepositoryImpl
import com.sunshine.app.domain.repository.ElevationRepository
import com.sunshine.app.domain.repository.SettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

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
            ).build()
        }

        // DAOs
        single { get<SunshineDatabase>().elevationDao() }

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

        // Repositories
        single<SettingsRepository> { SettingsRepositoryImpl(context = androidContext()) }
        single<ElevationRepository> {
            ElevationRepositoryImpl(
                elevationDao = get(),
                elevationApi = get(),
            )
        }
    }

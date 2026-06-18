package com.grape.mobile.app

import android.app.Application
import com.grape.mobile.database.DatabaseHelper
import com.grape.mobile.ble.GrapeBleManager
import com.grape.mobile.repository.DeviceRepository
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module
import timber.log.Timber

class GrapeApplication : Application() {
    override fun onCreate() {
        System.setProperty("uniffi.component.grape.libraryOverride", "grape_core")
        super.onCreate()
        
        Timber.plant(Timber.DebugTree())
        Timber.d("GrapeApplication initialized")

        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@GrapeApplication)
            modules(
                databaseModule,
                rustModule,
                bleModule,
                healthModule,
                workerModule
            )
        }
    }
}

val databaseModule = module {
    single { DatabaseHelper(androidContext()) }
}

val rustModule = module {
    single { DeviceRepository(get()) }
}

val bleModule = module {
    single { GrapeBleManager(androidContext(), get()) }
}

val healthModule = module {
    // Empty/Stub Module for health components
}

val workerModule = module {
    // Empty/Stub Module for WorkManager workers
}

package com.grape.mobile.app

import android.app.Application
import com.grape.mobile.database.DatabaseHelper
import com.grape.mobile.ble.GrapeBleManager
import com.grape.mobile.repository.DeviceRepository
import com.grape.mobile.repository.UpdateRepository
import com.grape.mobile.cdm.AssociationRepository
import com.grape.mobile.cdm.CompanionAssociationManager
import com.grape.mobile.cdm.DeviceDiagnostics
import com.grape.mobile.repository.DeviceSettingsRepository
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module
import timber.log.Timber

class GrapeApplication : Application() {
    override fun onCreate() {
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
                updateModule,
                healthModule,
                workerModule
            )
        }
    }
}

val databaseModule = module {
    single { DatabaseHelper(androidContext()) }
    single { AssociationRepository(get()) }
    single { DeviceSettingsRepository(get()) }
    single { DeviceDiagnostics(androidContext(), get(), get(), get()) }
}

val rustModule = module {
    single { DeviceRepository(get()) }
}

val bleModule = module {
    single { GrapeBleManager(androidContext(), get(), get()) }
    single { CompanionAssociationManager(androidContext(), get()) }
}

val updateModule = module {
    single { UpdateRepository(androidContext()) }
}

val healthModule = module {
    // Empty/Stub Module for health components
}

val workerModule = module {
    // Empty/Stub Module for WorkManager workers
}

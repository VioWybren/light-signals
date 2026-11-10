package nodomain.greensnowapps.lightsignals.moduls

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import nodomain.greensnowapps.lightsignals.model.WearSoundPoolPlayer
import nodomain.greensnowapps.lightsignals.model.WearSyncStateRepository
import javax.inject.Singleton

private const val DATASTORE_NAME = "Light_Signals_application_preferences"

@Module
@InstallIn(SingletonComponent::class)
object WearLightSignalsAppModule {


    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext appContext: Context) =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { appContext.preferencesDataStoreFile(DATASTORE_NAME) }
        )

    @Provides
    @Singleton
    fun provideWearSoundPoolPlayer(@ApplicationContext context: Context): WearSoundPoolPlayer {
        return WearSoundPoolPlayer(context)
    }

    @Provides
    @Singleton
    fun provideDataClient(@ApplicationContext context: Context): DataClient {
        return Wearable.getDataClient(context)
    }

    @Provides
    @Singleton
    fun provideWearControlStateRepository(
        dataClient: DataClient,
        messageClient: MessageClient
    ): WearSyncStateRepository {
        return WearSyncStateRepository(dataClient, messageClient)
    }

    @Provides
    @Singleton
    fun provideMessageClient(@ApplicationContext context: Context  ): MessageClient {
        return Wearable.getMessageClient(context)
    }
}

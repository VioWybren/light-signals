package nodomain.greensnowapps.lightsignals.moduls

import android.content.Context
import android.hardware.camera2.CameraManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import nodomain.greensnowapps.lightsignals.model.PhoneSoundPoolPlayer
import nodomain.greensnowapps.lightsignals.model.PhoneSyncStateRepository
import javax.inject.Singleton

private const val DATASTORE_NAME = "Light_Signals_application_preferences"

@Module
@InstallIn(SingletonComponent::class)
object LightSignalsAppModule {

    @Provides
    @Singleton
    fun provideCameraManager(@ApplicationContext ctx: Context): CameraManager =
        ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext appContext: Context) =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { appContext.preferencesDataStoreFile(DATASTORE_NAME) }
        )

    @Provides
    @Singleton
    fun provideSoundPoolPlayer(@ApplicationContext context: Context): PhoneSoundPoolPlayer {
        return PhoneSoundPoolPlayer(context)
    }

    @Provides
    @Singleton
    fun provideDataClient(@ApplicationContext context: Context): DataClient {
        return Wearable.getDataClient(context)
    }

    @Provides
    @Singleton
    fun providePhoneSyncStateRepository(
        @ApplicationContext context: Context,
        dataClient: DataClient,
        messageClient: MessageClient,
        dataStore: DataStore<Preferences>
    ): PhoneSyncStateRepository {
        return PhoneSyncStateRepository(context, dataClient, messageClient, dataStore)
    }

    @Provides
    @Singleton
    fun provideMessageClient(@ApplicationContext context: Context): MessageClient {
        return Wearable.getMessageClient(context)
    }
}

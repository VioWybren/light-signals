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

/**
 * Base filename of this app module's Preferences DataStore.
 */
private const val DATASTORE_NAME = "Light_Signals_application_preferences"

/**
 * Provides application-scoped phone preferences, hardware, audio and Wearable dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object LightSignalsAppModule {

    /**
     * Provides the camera service used by the torch adapter through Hilt.
     *
     * @param ctx Application context used to obtain the platform service.
     * @return The camera service used by the torch adapter.
     */
    @Provides
    @Singleton
    fun provideCameraManager(@ApplicationContext ctx: Context): CameraManager =
        ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    /**
     * Provides the application-scoped Preferences DataStore through Hilt.
     *
     * @param appContext Application context that owns the preferences file.
     * @return The application-scoped Preferences DataStore.
     */
    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext appContext: Context) =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { appContext.preferencesDataStoreFile(DATASTORE_NAME) }
        )

    /**
     * Provides the application-scoped phone sound player through Hilt.
     *
     * @param context Application context used to access platform services without retaining an
     *     Activity.
     * @return The application-scoped phone sound player.
     */
    @Provides
    @Singleton
    fun provideSoundPoolPlayer(@ApplicationContext context: Context): PhoneSoundPoolPlayer {
        return PhoneSoundPoolPlayer(context)
    }

    /**
     * Provides the Wearable DataItem client through Hilt.
     *
     * @param context Application context used to access platform services without retaining an
     *     Activity.
     * @return The Wearable DataItem client.
     */
    @Provides
    @Singleton
    fun provideDataClient(@ApplicationContext context: Context): DataClient {
        return Wearable.getDataClient(context)
    }

    /**
     * Provides the phone state and watch-session transport through Hilt.
     *
     * @param context Application context used to access platform services without retaining an
     *     Activity.
     * @param dataClient Wearable client used for legacy DataItem transport.
     * @param messageClient Wearable client used for request/reply messaging.
     * @param dataStore Preferences store used to read and persist settings.
     * @return The phone state and watch-session transport.
     */
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

    /**
     * Provides the Wearable request/reply client through Hilt.
     *
     * @param context Application context used to access platform services without retaining an
     *     Activity.
     * @return The Wearable request/reply client.
     */
    @Provides
    @Singleton
    fun provideMessageClient(@ApplicationContext context: Context): MessageClient {
        return Wearable.getMessageClient(context)
    }
}

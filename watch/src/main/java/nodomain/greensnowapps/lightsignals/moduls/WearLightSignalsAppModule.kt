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

/**
 * Base filename of this app module's Preferences DataStore.
 */
private const val DATASTORE_NAME = "Light_Signals_application_preferences"

/**
 * Provides application-scoped watch preferences, audio and Wearable dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object WearLightSignalsAppModule {


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
     * Provides the application-scoped watch sound player through Hilt.
     *
     * @param context Application context used to access platform services without retaining an
     *     Activity.
     * @return The application-scoped watch sound player.
     */
    @Provides
    @Singleton
    fun provideWearSoundPoolPlayer(@ApplicationContext context: Context): WearSoundPoolPlayer {
        return WearSoundPoolPlayer(context)
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
     * Provides the watch state and command transport through Hilt.
     *
     * @param dataClient Wearable client used for legacy DataItem transport.
     * @param messageClient Wearable client used for request/reply messaging.
     * @return The watch state and command transport.
     */
    @Provides
    @Singleton
    fun provideWearControlStateRepository(
        dataClient: DataClient,
        messageClient: MessageClient
    ): WearSyncStateRepository {
        return WearSyncStateRepository(dataClient, messageClient)
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
    fun provideMessageClient(@ApplicationContext context: Context  ): MessageClient {
        return Wearable.getMessageClient(context)
    }
}

package nodomain.greensnowapps.lightsignals.moduls

import android.content.Context
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.data.WearDataLayerRegistry
import com.google.android.horologist.datalayer.phone.PhoneDataLayerAppHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifies the coroutine scope shared by application-lifetime Data Layer components.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * Provides the application coroutine scope and Horologist Data Layer dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataLayerModule {

    /**
     * Provides the application-lifetime SupervisorJob + IO coroutine scope through Hilt.
     *
     * @return The application-lifetime SupervisorJob + IO coroutine scope.
     */
    @ApplicationScope
    @Provides
    @Singleton
    fun provideAppCoroutineScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Provides the Horologist Data Layer registry through Hilt.
     *
     * @param context Application context used to access platform services without retaining an
     *     Activity.
     * @param appScope Application-lifetime coroutine scope for Data Layer components.
     * @return The Horologist Data Layer registry.
     */
    @OptIn(ExperimentalHorologistApi::class)
    @Provides
    @Singleton
    fun provideWearDataLayerRegistry(
        @ApplicationContext context: Context,
        @ApplicationScope appScope: CoroutineScope
    ): WearDataLayerRegistry =
        WearDataLayerRegistry.fromContext(application = context, coroutineScope = appScope)

    /**
     * Provides the phone-side Horologist companion helper through Hilt.
     *
     * @param context Application context used to access platform services without retaining an
     *     Activity.
     * @param registry Shared Horologist Data Layer registry.
     * @return The phone-side Horologist companion helper.
     */
    @OptIn(ExperimentalHorologistApi::class)
    @Provides
    @Singleton
    fun providePhoneDataLayerAppHelper(
        @ApplicationContext context: Context,
        registry: WearDataLayerRegistry,
    ): PhoneDataLayerAppHelper = PhoneDataLayerAppHelper(
        context = context,
        registry = registry,
    )
}

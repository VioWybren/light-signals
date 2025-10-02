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

// 1. Define a qualifier for your Application‑wide CoroutineScope (optional but recommended)
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

// 2. A Module to supply the registry, scope, and helper
@Module
@InstallIn(SingletonComponent::class)
object DataLayerModule {

    @ApplicationScope
    @Provides
    @Singleton
    fun provideAppCoroutineScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(ExperimentalHorologistApi::class)
    @Provides
    @Singleton
    fun provideWearDataLayerRegistry(
        @ApplicationContext context: Context,
        @ApplicationScope appScope: CoroutineScope
    ): WearDataLayerRegistry =
        WearDataLayerRegistry.fromContext(application = context, coroutineScope = appScope)
    // register your serializers here, e.g.
    // .apply { registerSerializer(MyProtoSerializer) }

    @OptIn(ExperimentalHorologistApi::class)
    @Provides
    @Singleton
    fun providePhoneDataLayerAppHelper(
        @ApplicationContext context: Context,
        registry: WearDataLayerRegistry,
        @ApplicationScope appScope: CoroutineScope
    ): PhoneDataLayerAppHelper = PhoneDataLayerAppHelper(
        context       = context,
        registry      = registry,
    )
}

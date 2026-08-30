package com.adnanfoisal.infinitydesign.di

import android.content.Context
import com.adnanfoisal.infinitydesign.core.dispatchers.AppDispatchers
import com.adnanfoisal.infinitydesign.core.dispatchers.DefaultAppDispatchers
import com.adnanfoisal.infinitydesign.data.database.InfinityDatabase
import com.adnanfoisal.infinitydesign.data.preferences.PreferencesRepository
import com.adnanfoisal.infinitydesign.data.repositories.ProjectRepository
import com.adnanfoisal.infinitydesign.graphics.procedural.ProceduralRegistry
import com.adnanfoisal.infinitydesign.design.layout.CandidateGenerator
import com.adnanfoisal.infinitydesign.design.layout.LayoutEngine
import com.adnanfoisal.infinitydesign.design.typography.AndroidTypographyEngine
import com.adnanfoisal.infinitydesign.design.typography.HeuristicTypographyEngine
import com.adnanfoisal.infinitydesign.design.typography.TypographyEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): InfinityDatabase = InfinityDatabase.create(ctx)

    @Provides @Singleton
    fun provideProjectDao(db: InfinityDatabase) = db.projectDao()

    @Provides @Singleton
    fun provideBlueprintDao(db: InfinityDatabase) = db.blueprintDao()

    @Provides @Singleton
    fun provideProjectRepository(dao: com.adnanfoisal.infinitydesign.data.database.ProjectDao, bpDao: com.adnanfoisal.infinitydesign.data.database.BlueprintCacheDao): ProjectRepository =
        ProjectRepository(dao, bpDao)

    @Provides @Singleton
    fun providePreferences(@ApplicationContext ctx: Context): PreferencesRepository =
        PreferencesRepository(ctx)

    @Provides @Singleton
    fun provideProceduralRegistry(): ProceduralRegistry = ProceduralRegistry()

    @Provides @Singleton
    fun provideDispatchers(): AppDispatchers = DefaultAppDispatchers.create()

    @Provides @Singleton
    fun provideTypographyEngine(@ApplicationContext ctx: Context): TypographyEngine =
        try { AndroidTypographyEngine(ctx) } catch (_: Throwable) { HeuristicTypographyEngine() }

    @Provides @Singleton
    fun provideLayoutEngine(typography: TypographyEngine): LayoutEngine = LayoutEngine(typography)

    @Provides @Singleton
    fun provideCandidateGenerator(layout: LayoutEngine, typography: TypographyEngine): CandidateGenerator =
        CandidateGenerator(typography = typography, layoutEngine = layout)
}

package com.spiritwisestudios.inkrollers.di

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.spiritwisestudios.inkrollers.repository.ProfileRepository
import com.spiritwisestudios.inkrollers.ui.DialogManager
import com.spiritwisestudios.inkrollers.ui.GameSetupController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideProfileRepository(): ProfileRepository {
        return ProfileRepository
    }
} 
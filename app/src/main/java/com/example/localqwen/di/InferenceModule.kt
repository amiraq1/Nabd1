package com.example.localqwen.di

import android.content.Context
import com.example.localqwen.engine.LiteRtLmInferenceEngine
import com.example.localqwen.engine.NabdInferenceEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object InferenceModule {

    @Provides
    @Singleton
    fun provideInferenceEngine(
        @ApplicationContext context: Context
    ): NabdInferenceEngine {
        return LiteRtLmInferenceEngine(context)
    }
}

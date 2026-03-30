package com.looktube.app

import android.content.Context
import com.looktube.data.LocalCaptionEngineRegistry
import com.looktube.model.MoonshineLocalCaptionEngine
import com.looktube.model.WhisperCppLocalCaptionEngine

internal fun createLocalCaptionEngineRegistry(context: Context): LocalCaptionEngineRegistry =
    SelectableLocalCaptionEngineRegistry(
        runtimes = listOf(
            LocalCaptionEngineRuntime(
                engine = MoonshineLocalCaptionEngine,
                modelManager = ManagedMoonshineCaptionModelManager(context),
                generator = MoonshineLocalCaptionGenerator(context),
            ),
            LocalCaptionEngineRuntime(
                engine = WhisperCppLocalCaptionEngine,
                modelManager = ManagedLocalCaptionModelManager(context),
                generator = OnDeviceLocalCaptionGenerator(context),
            ),
        ),
        defaultEngineId = MoonshineLocalCaptionEngine.id,
    )

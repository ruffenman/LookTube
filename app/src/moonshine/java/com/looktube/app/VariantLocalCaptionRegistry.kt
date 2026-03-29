package com.looktube.app

import android.content.Context
import com.looktube.data.LocalCaptionEngineRegistry
import com.looktube.model.MoonshineLocalCaptionEngine
import com.looktube.model.WhisperCppLocalCaptionEngine

internal fun createLocalCaptionEngineRegistry(context: Context): LocalCaptionEngineRegistry =
    SelectableLocalCaptionEngineRegistry(
        runtimes = listOf(
            LocalCaptionEngineRuntime(
                engine = WhisperCppLocalCaptionEngine,
                modelManager = ManagedLocalCaptionModelManager(context),
                generator = OnDeviceLocalCaptionGenerator(context),
            ),
            LocalCaptionEngineRuntime(
                engine = MoonshineLocalCaptionEngine,
                modelManager = ManagedMoonshineCaptionModelManager(context),
                generator = MoonshineLocalCaptionGenerator(context),
            ),
        ),
        defaultEngineId = WhisperCppLocalCaptionEngine.id,
    )

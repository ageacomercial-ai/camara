package com.agea.camerapro

import androidx.camera.core.CameraEffect
import androidx.camera.core.SurfaceProcessor
import java.util.concurrent.Executor
import java.util.function.Consumer

/**
 * Subclasse concreta de CameraEffect.
 * O construtor de CameraEffect é protected, por isso não pode ser
 * instanciado directamente — esta subclasse expõe-o como public.
 * Aplica o mesmo SurfaceProcessor ao PREVIEW e ao VIDEO_CAPTURE,
 * para o filtro sair gravado no ficheiro .mp4 e não só no ecrã.
 */
class FilterEffect(
    surfaceProcessor: SurfaceProcessor,
    executor: Executor,
    onError: (Throwable) -> Unit
) : CameraEffect(
    PREVIEW or VIDEO_CAPTURE,
    executor,
    surfaceProcessor,
    Consumer { throwable -> onError(throwable) }
)

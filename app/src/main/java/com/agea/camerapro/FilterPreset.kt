package com.agea.camerapro

/**
 * One professional filter preset. Values mirror CSS filter() semantics so the
 * on-screen preview and the encoded recording match exactly (both are rendered
 * through the same GL pipeline in [CameraFilterProcessor]).
 */
data class FilterPreset(
    val id: String,
    val label: String,
    val grayscale: Float = 0f,      // 0..1
    val sepia: Float = 0f,          // 0..1
    val saturate: Float = 1f,       // 1 = unchanged
    val contrast: Float = 1f,       // 1 = unchanged
    val brightness: Float = 1f,     // 1 = unchanged
    val hueRotateDeg: Float = 0f    // degrees
) {
    companion object {
        val ALL = listOf(
            FilterPreset(id = "natural", label = "Natural"),
            FilterPreset(
                id = "cinema", label = "Cinema",
                sepia = 0.08f, saturate = 0.82f, contrast = 1.12f, brightness = 0.98f
            ),
            FilterPreset(
                id = "noir", label = "Noir P&B",
                grayscale = 1f, contrast = 1.25f, brightness = 1.02f
            ),
            FilterPreset(
                id = "vivid", label = "Vívido",
                saturate = 1.5f, contrast = 1.12f
            ),
            FilterPreset(
                id = "warm", label = "Quente",
                sepia = 0.28f, saturate = 1.25f, brightness = 1.05f
            ),
            FilterPreset(
                id = "cool", label = "Frio",
                hueRotateDeg = -8f, saturate = 1.12f, brightness = 1.02f, contrast = 1.04f
            ),
            FilterPreset(
                id = "vintage", label = "Vintage",
                sepia = 0.4f, contrast = 0.92f, brightness = 1.06f, saturate = 0.78f
            ),
            FilterPreset(
                id = "moody", label = "Dramático",
                contrast = 1.35f, brightness = 0.88f, saturate = 0.88f
            ),
        )
    }
}

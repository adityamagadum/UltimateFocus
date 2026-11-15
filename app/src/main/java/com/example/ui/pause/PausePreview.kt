package com.example.ui.pause

import com.example.data.model.PauseDesign

/**
 * Hands the editor's unsaved draft to the preview, so Preview shows exactly what is on
 * the editor without having to save it first.
 */
object PausePreview {
    @Volatile
    var design: PauseDesign? = null
}

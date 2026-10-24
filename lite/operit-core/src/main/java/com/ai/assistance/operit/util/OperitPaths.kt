package com.ai.assistance.operit.util

import java.io.File

// Retained image-pool path only; no public Downloads/storage permission is needed.
object OperitPaths {
    const val IMAGE_POOL_DIR_NAME = "image_pool"

    fun imagePoolDir(baseDir: File): File {
        return ensureDir(File(baseDir, IMAGE_POOL_DIR_NAME))
    }

    private fun ensureDir(dir: File): File {
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
}

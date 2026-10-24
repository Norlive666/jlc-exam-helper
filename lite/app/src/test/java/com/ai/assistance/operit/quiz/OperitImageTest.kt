package com.ai.assistance.operit.quiz

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.ai.assistance.operit.util.ImagePoolManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OperitImageTest {
    @After fun clearImages() { ImagePoolManager.clear() }

    @Test fun screenshotIsRealJpegAndKeepsAspectRatio() {
        val bitmap = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.RED)
            val jpeg = OperitScreenshot.encode(bitmap)
            val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            assertNotNull(decoded)
            assertEquals(720, decoded.width)
            assertEquals(1600, decoded.height)
            assertEquals(0xff, jpeg[0].toInt() and 0xff)
            assertEquals(0xd8, jpeg[1].toInt() and 0xff)
            decoded.recycle()
            assertFalse(bitmap.isRecycled)
            assertEquals(0, ImagePoolManager.size())
        } finally { bitmap.recycle() }
    }

    @Test fun smallScreensAreNotUpscaledAndEachFrameLeavesPoolEmpty() {
        val bitmap = Bitmap.createBitmap(200, 400, Bitmap.Config.ARGB_8888)
        try {
            repeat(3) {
                val jpeg = OperitScreenshot.encode(bitmap)
                val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                assertEquals(200, decoded.width)
                assertEquals(400, decoded.height)
                decoded.recycle()
                assertEquals(0, ImagePoolManager.size())
            }
        } finally { bitmap.recycle() }
    }
}

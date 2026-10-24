// Adapted from AAswordman/OperitAccessbility @ 1221cf08c40f48ee5dfa68d3fc2c5aacbbf01b6a.
// See lite/UPSTREAM.md for the exact retained methods and integration changes.
package com.ai.assistance.operit.provider

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.util.Xml
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.xmlpull.v1.XmlSerializer
import java.io.File
import java.io.StringWriter
import java.util.concurrent.CountDownLatch

open class UIAccessibilityService : AccessibilityService() {
    private val screenshotLock = Any()
    private var lastScreenshotTimestamp: Long = 0L
    private val minScreenshotIntervalMs: Long = 1100L

    fun performClick(x: Int, y: Int, completion: (Boolean) -> Unit): Boolean {
        Log.d(TAG, "准备在 ($x, $y) 执行点击...")

        // 1. 创建一个描述点击路径的Path对象
        // 明确地创建一个长度为0的线段，以确保路径的有效性
        val clickPath = android.graphics.Path().apply {
            moveTo(x.toFloat(), y.toFloat())
            lineTo(x.toFloat(), y.toFloat())
        }

        // 2. 用Path创建一个手势"笔划"
        // 点击的持续时间需要一个合理的值，不能太短，例如50毫秒
        val clickStroke = GestureDescription.StrokeDescription(clickPath, 0L, 50L)

        // 3. 用"笔划"构建完整的手势描述
        val gestureDescription = GestureDescription.Builder()
            .addStroke(clickStroke)
            .build()

        // 4. 保留原版手势构造，并将完成状态交给同进程的识题会话。
        val accepted = this@UIAccessibilityService.dispatchGesture(gestureDescription, object : AccessibilityService.GestureResultCallback() {
            // 同进程调用方等待完成回调，避免将接收请求当成点击完成。
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.i(TAG, "手势已成功完成。")
                completion(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "手势被取消。")
                completion(false)
            }
        }, null)
        if (!accepted) completion(false)
        return accepted
    }

    fun captureScreenshot(path: String, format: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false
        }
        check(android.os.Looper.myLooper() != android.os.Looper.getMainLooper())
        var resultValue = false
        val accepting = java.util.concurrent.atomic.AtomicBoolean(true)
        val normalizedFormat = format.lowercase()
        synchronized(screenshotLock) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastScreenshotTimestamp
            if (elapsed in 0 until minScreenshotIntervalMs) {
                try {
                    Thread.sleep(minScreenshotIntervalMs - elapsed)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
            lastScreenshotTimestamp = System.currentTimeMillis()
            val latch = CountDownLatch(1)
            this@UIAccessibilityService.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                this@UIAccessibilityService.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                        val hardwareBuffer = screenshotResult.hardwareBuffer
                        if (!accepting.get()) {
                            hardwareBuffer.close()
                            return
                        }
                        val colorSpace = screenshotResult.colorSpace
                        val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                        hardwareBuffer.close()
                        if (bitmap != null) {
                            try {
                                val file = File(path)
                                val parent = file.parentFile
                                if (parent != null && !parent.exists()) {
                                    parent.mkdirs()
                                }
                                val compressFormat = when (normalizedFormat) {
                                    "png" -> Bitmap.CompressFormat.PNG
                                    "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
                                    else -> Bitmap.CompressFormat.PNG
                                }
                                file.outputStream().use { output ->
                                    val quality = if (compressFormat == Bitmap.CompressFormat.JPEG) 90 else 100
                                    resultValue = bitmap.compress(compressFormat, quality, output)
                                }
                            } catch (_: Exception) {
                                resultValue = false
                            } finally {
                                bitmap.recycle()
                                if (!accepting.get()) File(path).delete()
                            }
                        } else {
                            resultValue = false
                        }
                        latch.countDown()
                    }

                    override fun onFailure(errorCode: Int) {
                        resultValue = false
                        latch.countDown()
                    }
                }
            )
            try {
                if (!latch.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    accepting.set(false)
                    return false
                }
            } catch (_: InterruptedException) {
                accepting.set(false)
                Thread.currentThread().interrupt()
                return false
            }
        }
        return resultValue
    }

    companion object {
        private const val TAG = "UIAccessibilityService"
        var isServiceConnected = false
            private set

        // Cache for the current activity name
        @Volatile
        var currentActivityName: String = ""
            internal set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceConnected = true
        Log.d(TAG, "服务已连接，状态更新为 true")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isServiceConnected = false
        currentActivityName = ""
        Log.d(TAG, "服务已解绑，状态更新为 false")
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Listen for window state changes to detect activity changes
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val className = event.className?.toString()
            if (!className.isNullOrEmpty()) {
                currentActivityName = className
                Log.d(TAG, "Activity changed to: $className")
            }
        }
    }

    override fun onInterrupt() {
        isServiceConnected = false
        currentActivityName = ""
        Log.d(TAG, "服务已中断，状态更新为 false")
    }

    fun captureUiHierarchyAsXml(): String {
        val rootNode = rootInActiveWindow ?: return ""
        val serializer = Xml.newSerializer()
        val writer = StringWriter()
        try {
            serializer.setOutput(writer)
            serializer.startDocument("UTF-8", true)
            serializeNodeToXml(rootNode, serializer)
            serializer.endDocument()
            return writer.toString()
        } catch (e: Exception) {
            Log.e(TAG, "生成UI XML时出错", e)
            return ""
        } finally {
            // serializeNodeToXml 已经递归回收了包括 rootNode 在内的所有节点，这里无需再次回收
        }
    }

    private fun serializeNodeToXml(node: AccessibilityNodeInfo?, serializer: XmlSerializer) {
        if (node == null) return
        serializer.startTag(null, "node")
        // 添加属性
        serializer.attribute(null, "class", node.className?.toString() ?: "")
        serializer.attribute(null, "package", node.packageName?.toString() ?: "")
        serializer.attribute(null, "content-desc", node.contentDescription?.toString() ?: "")
        serializer.attribute(null, "text", node.text?.toString() ?: "")
        serializer.attribute(null, "resource-id", node.viewIdResourceName ?: "")
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        serializer.attribute(null, "bounds", bounds.toShortString() ?: "")
        serializer.attribute(null, "clickable", node.isClickable.toString())
        serializer.attribute(null, "focused", node.isFocused.toString())
        serializer.attribute(null, "checkable", node.isCheckable.toString())
        serializer.attribute(null, "checked", node.isChecked.toString())
        serializer.attribute(null, "selected", node.isSelected.toString())

        for (i in 0 until node.childCount) {
            serializeNodeToXml(node.getChild(i), serializer)
        }
        serializer.endTag(null, "node")
        node.recycle()
    }
}

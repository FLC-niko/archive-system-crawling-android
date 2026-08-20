package com.topviewclub.crawling.service.handler

import android.os.Process
import android.os.Handler
import android.os.HandlerThread
import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.AutoOperationService

/**
 * 立即执行处理器，当接收到无障碍事件时，该处理器会立刻直接进行处理
 * */
class ImmediatelyProcessHandler : AccessibilityEventHandler() {

    private lateinit var handleThread: HandlerThread

    private lateinit var handler: Handler

    private var isActive = false

    override fun onServiceCreate(service: AutoOperationService) {
        isActive = true
        handleThread = HandlerThread(
            "ImmediatelyProcessHandler",
            Process.THREAD_PRIORITY_FOREGROUND
        )
        handleThread.start()
        handler = Handler(handleThread.looper)
    }

    @Suppress("DiscouragedPrivateApi")
    override fun onAccessibilityEvent(service: AutoOperationService, event: AccessibilityEvent) {
        if (!isActive) return
        val type = event.eventType
        handler.post{
            AccessibilityEvent::class.java.getDeclaredField("mEventType").apply {
                isAccessible = true
                set(event, type)
            }
            handleAccessibilityEvent(service, event)
        }
    }

    override fun post(r: Runnable) {
        handler.post(r)
    }

    override fun onServiceDestroy(service: AutoOperationService) {
        isActive = false
        handleThread.quit()
    }

}
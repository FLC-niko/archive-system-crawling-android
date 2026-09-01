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

    private var handleThread: HandlerThread? = null

    private var handler: Handler? = null

    private var isActive = false

    override fun onServiceCreate(service: AutoOperationService) {
        isActive = true
        val thread = HandlerThread(
            "ImmediatelyProcessHandler",
            Process.THREAD_PRIORITY_FOREGROUND
        )
        thread.start()
        handleThread = thread
        handler = Handler(thread.looper)
    }

    override fun onAccessibilityEvent(service: AutoOperationService, event: AccessibilityEvent) {
        if (!isActive) return
        val currentHandler = handler ?: return
        // AccessibilityEvent 来自系统对象池，回调返回后不能把原对象交给后台线程。
        val eventCopy = AccessibilityEvent.obtain(event)
        val posted = currentHandler.post {
            try {
                if (isActive) {
                    handleAccessibilityEvent(service, eventCopy)
                }
            } finally {
                eventCopy.recycle()
            }
        }
        if (!posted) eventCopy.recycle()
    }

    override fun post(r: Runnable) {
        if (isActive) handler?.post(r)
    }

    override fun onServiceDestroy(service: AutoOperationService) {
        isActive = false
        handler?.removeCallbacksAndMessages(null)
        handleThread?.quitSafely()
        handler = null
        handleThread = null
    }

}

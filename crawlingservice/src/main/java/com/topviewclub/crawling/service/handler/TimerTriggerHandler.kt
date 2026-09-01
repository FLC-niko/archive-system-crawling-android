package com.topviewclub.crawling.service.handler

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.TimerThread

/**
 * 时间间隔处理器，在指定的时间间隔触发无障碍事件，无论是否接收到新的无障碍事件
 * */
class TimerTriggerHandler(
    private val interval: Long
) : AccessibilityEventHandler() {

    private var autoOperationService: AutoOperationService? = null

    private var accessibilityEvent: AccessibilityEvent? = null

    private val eventLock = Any()

    private var handleThread: TimerThread? = null

    override fun onServiceCreate(service: AutoOperationService) {
        autoOperationService = service
        handleThread = TimerThread("TimerTriggerHandler", interval) {
            val event = synchronized(eventLock) {
                accessibilityEvent?.let(AccessibilityEvent::obtain)
            } ?: return@TimerThread true
            try {
                autoOperationService?.let { handleAccessibilityEvent(it, event) } ?: false
            } finally {
                event.recycle()
            }
        }
    }

    override fun onAccessibilityEvent(service: AutoOperationService, event: AccessibilityEvent) {
        val eventCopy = AccessibilityEvent.obtain(event)
        synchronized(eventLock) {
            accessibilityEvent?.recycle()
            accessibilityEvent = eventCopy
        }
    }

    override fun post(r: Runnable) {
        handleThread?.post(r)
    }

    override fun onServiceDestroy(service: AutoOperationService) {
        handleThread?.quit()
        handleThread = null
        autoOperationService = null
        synchronized(eventLock) {
            accessibilityEvent?.recycle()
            accessibilityEvent = null
        }
    }

}

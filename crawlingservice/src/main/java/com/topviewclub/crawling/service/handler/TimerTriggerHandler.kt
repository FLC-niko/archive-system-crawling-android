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

    private lateinit var autoOperationService: AutoOperationService

    private var accessibilityEvent: AccessibilityEvent? = null

    private var eventType: Int = 0

    private lateinit var handleThread: TimerThread

    @Suppress("DiscouragedPrivateApi")
    override fun onServiceCreate(service: AutoOperationService) {
        autoOperationService = service
        handleThread = TimerThread("TimerTriggerHandler", interval) {
            val event = accessibilityEvent ?: return@TimerThread true
            AccessibilityEvent::class.java.getDeclaredField("mEventType").apply {
                isAccessible = true
                set(event, eventType)
            }
            handleAccessibilityEvent(autoOperationService, event)
        }
    }

    override fun onAccessibilityEvent(service: AutoOperationService, event: AccessibilityEvent) {
        accessibilityEvent = event
        eventType = event.eventType
    }

    override fun post(r: Runnable) {
        handleThread.post(r)
    }

    override fun onServiceDestroy(service: AutoOperationService) {
        handleThread.quit()
    }

}
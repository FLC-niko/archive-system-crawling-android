package com.topviewclub.crawling.service.handler

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.AutoOperationService

abstract class AccessibilityEventHandler {

    open fun onServiceCreate(service: AutoOperationService) {
    }

    /**
     * 接收到新的无障碍事件时，该函数将得到回调
     * */
    abstract fun onAccessibilityEvent(
        service: AutoOperationService,
        event: AccessibilityEvent
    )

    abstract fun post(r: Runnable)

    open fun onServiceDestroy(service: AutoOperationService) {
    }

    /**
     * 执行此函数，进行一次无障碍事件的调度
     *
     * @param event 要被调度的无障碍事件
     * */
    protected fun handleAccessibilityEvent(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): Boolean {
        return service.handleEvent(event)
    }

}
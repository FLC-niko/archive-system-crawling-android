package com.topviewclub.crawling.service.action

import androidx.annotation.IntRange
import android.view.accessibility.AccessibilityEvent
import com.topviewclub.common.bean.TaskResultType
import com.topviewclub.crawling.service.*

/**
 * 一个空步骤，服务 15 秒内步骤没有发生改变将会被认定为无响应，如果某一步骤需要运行的时间很长，
 * 可以使用这个类来作为一个中间步骤来跳转，请谨慎使用此类，避免服务无响应而不报错的情况发生
 * */
class EmptyAction(
    override val actionName: String,
    private val nextActionName: String,
    /**
     * 大于 15 秒有直接造成 [TaskResultType.SERVICE_NO_RESPONSE] 的风险
     * */
    @IntRange(from = 0L, to = 15000L)
    private val delay: Long = 10L
) : Action {

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        try {
            Thread.sleep(delay)
            return nextActionName
        } finally {
            service.resumeServiceDelay(event, 0L)
        }
    }

}
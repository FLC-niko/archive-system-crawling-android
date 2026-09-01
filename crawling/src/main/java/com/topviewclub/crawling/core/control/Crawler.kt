package com.topviewclub.crawling.core.control

import android.provider.Settings
import com.topviewclub.common.base.appContext
import com.topviewclub.common.log.logE
import com.topviewclub.common.mq.RabbitTaskContext

internal abstract class Crawler {

    /**
     * 对应无障碍服务的类名
     * */
    abstract val serviceClassName: String

    /**
     * 开始抓取
     *
     * @param target 目标账号名
     * @param tag 标记
     * @param startDate 起始日期
     * @param endDate 终止日期
     * */
    abstract fun startCrawling(
        target: String?,
        tag: String?,
        startDate: Long,
        endDate: Long,
        rabbitTaskContext: RabbitTaskContext? = null,
    )

    /**
     * 根据给定的无障碍服务 [serviceClassName] ，启动无障碍服务
     * */
    protected fun startAccessibilityService() {
        // 普通应用没有 WRITE_SECURE_SETTINGS，不能通过 ContentResolver 强行开启服务。
        // 服务授权由系统设置完成；这里只检查当前任务所需的服务是否已开启，避免
        // Android 14 上因 SecurityException 直接杀死 AAOS 进程。
        val enabledServices = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty().split(':').filter { it.isNotBlank() }
        if (serviceClassName !in enabledServices) {
            logE(
                "Crawler",
                "Accessibility service is not enabled: $serviceClassName",
            )
        }
    }

}

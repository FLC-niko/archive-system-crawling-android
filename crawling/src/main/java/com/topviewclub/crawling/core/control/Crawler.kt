package com.topviewclub.crawling.core.control

import android.provider.Settings
import com.topviewclub.common.base.appContext

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
    abstract fun startCrawling(target: String?, tag: String?, startDate: Long, endDate: Long)

    /**
     * 根据给定的无障碍服务 [serviceClassName] ，启动无障碍服务
     * */
    protected fun startAccessibilityService() {
        Settings.Secure.putString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            serviceClassName
        )
        Settings.Secure.putInt(
            appContext.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED, 1
        )
    }

}
package com.topviewclub.crawling.core.base

import android.os.Build
import com.topviewclub.common.base.BaseApplication
import com.topviewclub.crawling.core.control.TaskDispatcher


open class CrawlApplication : BaseApplication() {

    override fun onCreate() {
        super.onCreate()
        // RabbitMQ/无障碍任务通道随应用进程自动启动，不再依赖界面上的人工点击。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            TaskDispatcher.init()
        }
    }
}

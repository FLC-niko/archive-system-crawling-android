package com.topviewclub.crawling.core.control

import com.topviewclub.common.base.appContext
import com.topviewclub.common.util.startXueXiActivity
import com.topviewclub.crawling.xuexi.XueXiOperationService

internal abstract class AbstractXueXiCrawler : Crawler() {
    /**
     * 抓取学习强国 APP 内容
     *
     * @param target 抓取的目标账号名
     * @param startDate 抓取的起始日期，如果是抓取全部，赋值为非正数即可
     * */
    final override fun startCrawling(
        target: String?,
        tag: String?,
        startDate: Long,
        endDate: Long
    ) {
        initData(target, tag, startDate, endDate)
        startAccessibilityService()
        appContext.startXueXiActivity()
    }

    abstract fun initData(target: String?, tag: String?, startDate: Long, endDate: Long)
}

internal object XueXiCrawler : AbstractXueXiCrawler() {
    override val serviceClassName: String =
        "${appContext.packageName}/com.topviewclub.crawling.xuexi.XueXiOperationService"

    override fun initData(target: String?, tag: String?, startDate: Long, endDate: Long) {
        XueXiOperationService.prepare(
            serviceTag = tag,
            startDate = startDate,
            endDate = endDate,
            account = target
        )
    }
}

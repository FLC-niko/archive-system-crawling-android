package com.topviewclub.crawling.core.control

import com.topviewclub.common.base.appContext
import com.topviewclub.common.mq.RabbitTaskContext
import com.topviewclub.common.util.startWechatScanActivity
import com.topviewclub.crawling.service.wechat.check.CheckQRCodeOperationService
import com.topviewclub.crawling.wechat.auto.AutoChatOperationService
import com.topviewclub.crawling.wechat.official.OfficialOperationService
import com.topviewclub.crawling.wechat.video.VideoOperationService

/**
 * 抓取微信 APP 内容的抽象类
 * */
internal abstract class AbstractWechatCrawler : Crawler() {
    /**
     * 抓取微信 APP 内容
     *
     * @param target 抓取的目标账号名
     * @param startDate 抓取的起始日期，如果是抓取全部，赋值为非正数即可
     * */
    final override fun startCrawling(
        target: String?,
        tag: String?,
        startDate: Long,
        endDate: Long,
        rabbitTaskContext: RabbitTaskContext?,
    ) {
        initData(target, tag, startDate, endDate, rabbitTaskContext)
        startAccessibilityService()
        appContext.startWechatScanActivity()
    }

    abstract fun initData(
        target: String?,
        tag: String?,
        startDate: Long,
        endDate: Long,
        rabbitTaskContext: RabbitTaskContext?,
    )
}

/**
 * 抓取微信公众号文章的单例
 * */
internal object WechatOfficialCrawler : AbstractWechatCrawler() {
    override val serviceClassName =
        "${appContext.packageName}/com.topviewclub.crawling.wechat.official.OfficialOperationService"

    override fun initData(
        target: String?,
        tag: String?,
        startDate: Long,
        endDate: Long,
        rabbitTaskContext: RabbitTaskContext?,
    ) {
        OfficialOperationService.prepare(
            serviceTag = tag,
            startDate = startDate,
            endDate = endDate,
            account = target,
            rabbitTaskContext = rabbitTaskContext,
        )
    }
}

/**
 * 抓取微信视频号视频的单例
 * */
internal object WechatVideoCrawler : AbstractWechatCrawler() {
    override val serviceClassName =
        "${appContext.packageName}/com.topviewclub.crawling.wechat.video.VideoOperationService"

    override fun initData(
        target: String?,
        tag: String?,
        startDate: Long,
        endDate: Long,
        rabbitTaskContext: RabbitTaskContext?,
    ) {
        VideoOperationService.prepare(
            serviceTag = tag,
            startDate = startDate,
            account = target
        )
    }
}

/**
 * 自动回复微信的单例
 * */
internal object WechatAutoChatCrawler : AbstractWechatCrawler() {
    override val serviceClassName =
        "${appContext.packageName}/com.topviewclub.crawling.wechat.auto.AutoChatOperationService"

    override fun initData(
        target: String?,
        tag: String?,
        startDate: Long,
        endDate: Long,
        rabbitTaskContext: RabbitTaskContext?,
    ) {
        AutoChatOperationService.prepare(
            serviceTag = tag
        )
    }
}

/**
 * 检查微信二维码是否有效的单例
 * */
internal object WechatQRCodeCheckCrawler : AbstractWechatCrawler() {
    override val serviceClassName =
        "${appContext.packageName}/com.topviewclub.crawling.service.wechat.check.CheckQRCodeOperationService"

    override fun initData(
        target: String?,
        tag: String?,
        startDate: Long,
        endDate: Long,
        rabbitTaskContext: RabbitTaskContext?,
    ) {
        CheckQRCodeOperationService.prepare(
            serviceTag = tag,
            account = target
        )
    }
}

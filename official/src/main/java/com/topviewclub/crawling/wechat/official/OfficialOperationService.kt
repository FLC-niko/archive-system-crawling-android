package com.topviewclub.crawling.wechat.official

import com.topviewclub.common.bean.AAOSTask
import com.topviewclub.common.bean.TaskCrawlingType
import com.topviewclub.common.bean.TaskResultType
import com.topviewclub.crawling.service.action.ActionException
import com.topviewclub.crawling.service.wechat.WechatOperationService
import com.topviewclub.crawling.service.wechat.check.CheckQRCodeOperationService
import com.topviewclub.crawling.wechat.official.action.*

class OfficialOperationService : WechatOperationService() {

    companion object {
        private var tag: String? = null
        private var targetStartDate = Long.MIN_VALUE
        private var targetEndDate: Long = Long.MAX_VALUE
        private var targetAccount: String? = null

        /**
         * 开启服务前调用此函数初始化参数
         * */
        fun prepare(
            serviceTag: String?,
            startDate: Long,
            endDate: Long,
            account: String?
        ) {
            tag = serviceTag
            targetStartDate = startDate
            targetEndDate = endDate
            targetAccount = account
            officialArticleSetInternal.clear()

        }
    }

    override val crawlServiceType: String = TaskCrawlingType.TYPE_OFFICIAL

    override val aaosTask: AAOSTask = AAOSTask(
        TaskCrawlingType.TYPE_OFFICIAL,
        tag,
        targetAccount,
        startDate,
        endDate
    )

    override val target: String
        get() {
            val value = targetAccount
            value ?: throw ActionException(TaskResultType.TARGET_IS_NULL)
            return value
        }

    override val serviceTag: String? get() = tag

    override val firstlyTargetActionName: String = "HomingOfficialList"

    override val wechatChain = listOf(
        HomingOfficialList(),
        CheckOfficialEndDate(),
        ScrollOfficialList(),
        EnterOfficialArticle(),
        OpenMoreEnum(),
        CopyOfficialArticleURL(),
        GetOfficialArticleURL(),
        BackToOfficialArticleList(),
        WriteOfficialArticle(),
        ExitOfficialArticleList(),
        EnterWechatLauncher()
    )

    override val startDate: Long get() = targetStartDate

    override val endDate: Long get() = targetEndDate

}
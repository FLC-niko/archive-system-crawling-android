package com.topviewclub.crawling.service.wechat.check

import com.topviewclub.common.bean.AAOSTask
import com.topviewclub.common.bean.TaskCrawlingType
import com.topviewclub.common.bean.TaskResultType
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.action.ActionException
import com.topviewclub.crawling.service.wechat.WechatOperationService

class CheckQRCodeOperationService : WechatOperationService() {

    companion object {
        private var tag: String? = null
        private var targetAccount: String? = null

        /**
         * 开启服务前调用此函数初始化参数
         * */
        fun prepare(
            serviceTag: String?,
            account: String?
        ) {
            tag = serviceTag
            targetAccount = account
        }
    }

    override val crawlServiceType: String = TaskCrawlingType.TYPE_CHECK_WECHAT_QRCODE

    override val aaosTask: AAOSTask = AAOSTask(
        TaskCrawlingType.TYPE_CHECK_WECHAT_QRCODE,
        tag,
        targetAccount
    )

    override val serviceTag: String? get() = tag

    override val firstlyTargetActionName: String = ActionType.ActionSuccess

    override val wechatChain: List<Action> = emptyList()

    override val target: String
        get() {
            val value = targetAccount
            value ?: throw ActionException(TaskResultType.TARGET_IS_NULL)
            return value
        }

}
package com.topviewclub.crawling.wechat.official

import com.topviewclub.common.bean.AAOSTask
import com.topviewclub.common.bean.TaskCrawlingType
import com.topviewclub.common.bean.TaskResultType
import com.topviewclub.common.mq.RabbitTaskContext
import com.topviewclub.common.storage.official.OfficialArticleWriter
import com.topviewclub.crawling.service.ServiceResult
import com.topviewclub.crawling.service.action.ActionException
import com.topviewclub.crawling.service.wechat.WechatOperationService
import com.topviewclub.crawling.wechat.official.action.*

class OfficialOperationService : WechatOperationService() {

    companion object {
        private var tag: String? = null
        private var targetStartDate = Long.MIN_VALUE
        private var targetEndDate: Long = Long.MAX_VALUE
        private var targetAccount: String? = null
        private var preparedRabbitTaskContext: RabbitTaskContext? = null

        /**
         * 开启服务前调用此函数初始化参数
         * */
        fun prepare(
            serviceTag: String?,
            startDate: Long,
            endDate: Long,
            account: String?,
            rabbitTaskContext: RabbitTaskContext? = null,
        ) {
            tag = serviceTag
            targetStartDate = startDate
            targetEndDate = endDate
            targetAccount = account
            preparedRabbitTaskContext = rabbitTaskContext
            officialArticleSetInternal.clear()

        }
    }

    override val crawlServiceType: String = TaskCrawlingType.TYPE_OFFICIAL

    override val aaosTask: AAOSTask = AAOSTask(
        TaskCrawlingType.TYPE_OFFICIAL,
        tag,
        targetAccount,
        startDate,
        endDate,
        rabbitTaskContext = preparedRabbitTaskContext,
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

    override fun onCreate() {
        addOnServiceDestroyListener { result ->
            when (result) {
                is ServiceResult.Completed ->
                    if (aaosTask.rabbitTaskContext != null) {
                        // WriteOfficialArticle 已触发 V2 结果发布；这里仅保留幂等兜底。
                        OfficialArticleWriter.sendOfficialArticleSetToBigData(
                            officialArticleSetInternal,
                            aaosTask,
                        )
                    }
                is ServiceResult.Error ->
                    OfficialArticleWriter.sendRabbitFailure(
                        aaosTask,
                        code = result.msg,
                        message = "公众号抓取服务失败: ${result.msg}",
                    )
            }
        }
        super.onCreate()
    }

    override fun onUnexpectedServiceDestroy() {
        OfficialArticleWriter.sendRabbitFailure(
            aaosTask,
            code = TaskResultType.SERVICE_DESTROY_UNEXPECTEDLY,
            message = "公众号无障碍服务意外销毁",
        )
    }

}

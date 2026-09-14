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

data class OfficialTaskSession(
    val serviceTag: String?,
    val startDate: Long = Long.MIN_VALUE,
    val endDate: Long = Long.MAX_VALUE,
    val targetAccount: String?,
    val rabbitTaskContext: RabbitTaskContext? = null,
)

class OfficialOperationService : WechatOperationService() {

    companion object {
        @Volatile
        private var session: OfficialTaskSession? = null

        /**
         * 开启服务前调用此函数初始化任务会话参数
         * */
        fun prepare(
            serviceTag: String?,
            startDate: Long,
            endDate: Long,
            account: String?,
            rabbitTaskContext: RabbitTaskContext? = null,
        ) {
            session = OfficialTaskSession(
                serviceTag = serviceTag,
                startDate = startDate,
                endDate = endDate,
                targetAccount = account,
                rabbitTaskContext = rabbitTaskContext,
            )
            officialArticleSetInternal.clear()
        }

        fun clearSession() {
            session = null
            officialArticleSetInternal.clear()
        }
    }

    override val crawlServiceType: String = TaskCrawlingType.TYPE_OFFICIAL

    override val aaosTask: AAOSTask
        get() {
            val s = session
            return AAOSTask(
                TaskCrawlingType.TYPE_OFFICIAL,
                s?.serviceTag,
                s?.targetAccount,
                s?.startDate ?: Long.MIN_VALUE,
                s?.endDate ?: Long.MAX_VALUE,
                rabbitTaskContext = s?.rabbitTaskContext,
            )
        }

    override val target: String
        get() = session?.targetAccount ?: throw ActionException(TaskResultType.TARGET_IS_NULL)

    override val serviceTag: String? get() = session?.serviceTag

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

    override val startDate: Long get() = session?.startDate ?: Long.MIN_VALUE

    override val endDate: Long get() = session?.endDate ?: Long.MAX_VALUE

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
            clearSession()
        }
        super.onCreate()
    }

    override fun onDestroy() {
        super.onDestroy()
        clearSession()
    }

    override fun onUnexpectedServiceDestroy() {
        OfficialArticleWriter.sendRabbitFailure(
            aaosTask,
            code = TaskResultType.SERVICE_DESTROY_UNEXPECTEDLY,
            message = "公众号无障碍服务意外销毁",
        )
        clearSession()
    }

}

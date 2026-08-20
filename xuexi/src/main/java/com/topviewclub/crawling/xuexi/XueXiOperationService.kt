package com.topviewclub.crawling.xuexi

import com.topviewclub.common.bean.AAOSTask
import com.topviewclub.common.bean.PackageNames
import com.topviewclub.common.bean.TaskCrawlingType
import com.topviewclub.common.bean.TaskResultType
import com.topviewclub.common.network.sendMessageToHostErrorOnce
import com.topviewclub.common.shizuku.Shizuku_killApplication
import com.topviewclub.common.util.className
import com.topviewclub.common.util.toStringOrEmpty
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.ActionException
import com.topviewclub.crawling.service.action.EmptyAction
import com.topviewclub.crawling.xuexi.action.*

class XueXiOperationService : AutoOperationService() {

    companion object {
        private var tag: String? = null
        private var targetStartDate = Long.MIN_VALUE
        private var targetEndDate = Long.MAX_VALUE
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
            xueXiArticleSetInternal.clear()
        }
    }

    override val crawlServiceType: String = TaskCrawlingType.TYPE_XUE_XI

    override val aaosTask: AAOSTask = AAOSTask(
        TaskCrawlingType.TYPE_XUE_XI,
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

    override val actionList = listOf(
        EnterSearchActivity(),
        ScanTargetAccount(),
        EnterSearchResult(),
        EnterAccountHome(),
        EmptyAction("Empty0", "EnterXueXiArticle"),
        EnterXueXiArticle(),
        GetXueXiArticleInfoCompat(),
        WriteXueXiArticle()
    )

    override var targetActionName: String = "EnterSearchActivity"

    override val startDate: Long get() = targetStartDate

    override val endDate: Long get() = targetEndDate

    override fun onCreate() {
        super.onCreate()
        addOnServiceDestroyListener {
            runCatching {
                Shizuku_killApplication(PackageNames.PKG_XUE_XI)
            }.onFailure {
                sendMessageToHostErrorOnce(
                    this@XueXiOperationService.className,
                    TaskResultType.RESTART_XUE_XI,
                    serviceTag.toStringOrEmpty()
                )
            }
        }
    }
}
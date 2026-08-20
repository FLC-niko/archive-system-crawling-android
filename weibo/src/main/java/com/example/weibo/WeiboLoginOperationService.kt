package com.example.weibo

import com.topviewclub.common.bean.AAOSTask
import com.topviewclub.common.bean.TaskCrawlingType
import com.topviewclub.common.bean.TaskResultType
import com.topviewclub.common.bean.WeiboLoginTask
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.action.ActionException
import com.topviewclub.crawling.service.handler.AccessibilityEventHandler
import com.topviewclub.crawling.service.handler.TimerTriggerHandler

class WeiboLoginOperationService : AutoOperationService() {

    companion object {
        private var tag: String? = null


        internal var title = ""

        /**
         * 开启服务前调用此函数初始化参数
         * */
        fun prepare(
            serviceTag: String?
        ) {
            tag = serviceTag
        }

        internal var prepareToShutdown: Boolean = false

        fun shutdownACService() {
            prepareToShutdown = true
        }



    }

    override val crawlServiceType: String = TaskCrawlingType.TYPE_WEIBO_LOGIN

    override val aaosTask: AAOSTask = WeiboLoginTask()

    override val eventHandler: AccessibilityEventHandler =
        TimerTriggerHandler(100L)

    override val target: String
        get() {
            throw ActionException(TaskResultType.TARGET_IS_NULL)
        }
    override val actionList: List<Action>  = listOf(


    )
    override var targetActionName: String = "EnterWeiboLauncher"
}
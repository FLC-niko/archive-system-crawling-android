package com.topviewclub.crawling.wechat.auto

import com.topviewclub.common.base.wechatVideoCacheCaptor
import com.topviewclub.common.bean.*
import com.topviewclub.common.log.logRabbit
import com.topviewclub.common.network.sendMessageToHostErrorOnce
import com.topviewclub.common.shizuku.Shizuku_killApplication
import com.topviewclub.common.util.className
import com.topviewclub.common.util.toStringOrEmpty
import com.topviewclub.common.wirebare.startWechatVideoProxy
import com.topviewclub.common.wirebare.stopWireBareProxy
import com.topviewclub.crawling.service.*
import com.topviewclub.crawling.service.handler.AccessibilityEventHandler
import com.topviewclub.crawling.service.handler.TimerTriggerHandler
import com.topviewclub.crawling.service.action.EmptyAction
import com.topviewclub.crawling.service.action.ActionException
import com.topviewclub.crawling.wechat.auto.action.*
import com.topviewclub.crawling.wechat.auto.action.EnterWechatLauncher
import com.topviewclub.crawling.wechat.auto.room.acv.REQUEST_TYPE_UNDEFINED
import com.topviewclub.crawling.wechat.auto.room.acv.RequestWechatType

class AutoChatOperationService : AutoOperationService() {

    companion object {
        private var tag: String? = null

        /**
         * 开启服务前调用此函数初始化参数
         * */
        fun prepare(
            serviceTag: String?
        ) {
            tag = serviceTag
            numberOfWechat = ""
            requestWechatType = REQUEST_TYPE_UNDEFINED
            videoURL = null
        }

        internal var prepareToShutdown: Boolean = false

        fun shutdownACService() {
            prepareToShutdown = true
        }

        internal var nameOfWechat = ""
        internal var numberOfWechat = ""
        internal var title = ""

        @RequestWechatType
        internal var requestWechatType = REQUEST_TYPE_UNDEFINED

        internal var videoURL: String? = null
    }

    /**
     * AC 服务不需要发送数据至主机，打印日志即可
     * */
    override val allowRecords: Boolean = false

    override val crawlServiceType: String = TaskCrawlingType.TYPE_AUTO_CHAT

    override val aaosTask: AAOSTask = AutoChatTask()

    override val eventHandler: AccessibilityEventHandler =
        TimerTriggerHandler(100L)

    override val target: String
        get() {
            throw ActionException(TaskResultType.TARGET_IS_NULL)
        }

    override val serviceTag: String? = tag

    override val actionList = listOf(
        EnterWechatLauncher(),
        EnterChatFragment(),
        HomingChatList(),
        EmptyAction("Empty0", "HomingChatList", 1 * 1000L),
        CheckNumberOfUnreadMessages(),
        EmptyAction("Empty1", "CheckNumberOfUnreadMessages", 5 * 1000L),
        EnterPersonalChat(),
        EnterChatInfo(),
        CheckPersonalChat(),
        EnterPersonalInfo(),
        GetWechatNumberAndName(),
        BackToChatInfo(),
        EnterVideo(),
        BackToPersonalChat(),
        ScanRequestCode(),
        SendWechatMessage(),
        BackToChatFragment(),
        ScrollChatList()
    )

    override var targetActionName: String = "EnterWechatLauncher"

    override fun onCreate() {
        super.onCreate()
        addOnServiceDestroyListener { result ->
            stopWireBareProxy()
            if (result is ServiceResult.Error) {
                runCatching {
                    Shizuku_killApplication(PackageNames.PKG_WECHAT)
                }.onFailure {
                    sendMessageToHostErrorOnce(
                        this@AutoChatOperationService.className,
                        TaskResultType.RESTART_MIRCO_MESSAGE,
                        serviceTag.toStringOrEmpty()
                    )
                    it.printStackTrace()
                }
            }
        }
        startWechatVideoProxy(::insertKV)
    }

    private fun insertKV(urlPair: Pair<String, String>) {
        videoURL = urlPair.second
        if(videoURL == null){
            logRabbit("crawling fail single ")
        }
    }

}
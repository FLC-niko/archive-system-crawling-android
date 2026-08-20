package com.topviewclub.crawling.service.wechat.weread

import com.topviewclub.common.bean.AAOSTask
import com.topviewclub.common.bean.TaskCrawlingType
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.wechat.action.ClickAlbum
import com.topviewclub.crawling.service.wechat.action.SelectPhoneOrOpenFolderList
import com.topviewclub.crawling.service.wechat.action.SelectQRCodeFolder
import com.topviewclub.crawling.service.wechat.action.StartWechatScanActivity
import com.topviewclub.crawling.service.wechat.WechatOperationService
import com.topviewclub.crawling.service.wechat.weread.action.ConfirmWeReadLogin
import com.topviewclub.crawling.service.wechat.weread.action.SelectWeReadPhoto

/** 微信读书专用登录责任链，不执行公众号主页和目标账号动作。 */
class WeReadLoginOperationService : WechatOperationService() {
    override val crawlServiceType: String = TaskCrawlingType.TYPE_WEREAD_LOGIN
    override val target: String = "weread"
    override val aaosTask: AAOSTask = AAOSTask(TaskCrawlingType.TYPE_WEREAD_LOGIN, serviceTag, target)
    override val firstlyTargetActionName: String = "StartWechatScanActivity"
    override val wechatChain: List<Action> = emptyList()
    override var targetActionName: String = firstlyTargetActionName

    override val actionList = mutableListOf<Action>(
        StartWechatScanActivity(),
        ClickAlbum(),
        SelectPhoneOrOpenFolderList(),
        SelectQRCodeFolder(),
        SelectWeReadPhoto(),
        ConfirmWeReadLogin(),
    )
}
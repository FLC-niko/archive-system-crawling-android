package com.topviewclub.crawling.service.wechat

import androidx.annotation.CallSuper
import com.topviewclub.common.bean.PackageNames
import com.topviewclub.common.bean.TaskResultType
import com.topviewclub.common.network.sendMessageToHostErrorOnce
import com.topviewclub.common.shizuku.Shizuku_killApplication
import com.topviewclub.common.util.className
import com.topviewclub.common.util.toStringOrEmpty
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.ServiceResult
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.wechat.action.*

/**
 * 微信模拟点击无障碍服务，执行默认责任链可以保证到达指定公众号主页
 * */
abstract class WechatOperationService : AutoOperationService() {

    /**
     * 自定义 [Action] ，将会被添加到 [actionList] 后，默认 [actionList] 保证到达公众号主页面
     * */
    abstract val wechatChain: List<Action>

    abstract val firstlyTargetActionName: String

    /**
     * 微信抓取责任链，执行完毕后保证到达公众号主页
     * */
    open override val actionList = mutableListOf(
        StartWechatScanActivity(),
        ClickAlbum(),
        SelectPhoneOrOpenFolderList(),
        SelectQRCodeFolder(),
        SelectPhoto(),
        EnterOfficialHome(),
        CheckTargetAccount()
    )

    override var targetActionName: String = "StartWechatScanActivity"

    @CallSuper
    override fun onCreate() {
        actionList.addAll(wechatChain)
        super.onCreate()
        addOnServiceDestroyListener { result ->
            if (result is ServiceResult.Error) {
                runCatching {
                    Shizuku_killApplication(PackageNames.PKG_WECHAT)
                }.onFailure {
                    sendMessageToHostErrorOnce(
                        this@WechatOperationService.className,
                        TaskResultType.RESTART_MIRCO_MESSAGE,
                        serviceTag.toStringOrEmpty()
                    )
                }
            }
        }
    }

}
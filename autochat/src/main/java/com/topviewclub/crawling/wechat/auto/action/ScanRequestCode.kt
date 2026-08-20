package com.topviewclub.crawling.wechat.auto.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.common.bean.TaskStat
import com.topviewclub.common.log.logI
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.CLS_TEXT_VIEW
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.findNodeOrNull
import com.topviewclub.crawling.service.text
import com.topviewclub.crawling.wechat.auto.AutoChatOperationService
import com.topviewclub.crawling.wechat.auto.room.ACLimitedDao
import com.topviewclub.crawling.wechat.auto.room.ACVideoDao
import com.topviewclub.crawling.wechat.auto.room.acv.ACVideo
import com.topviewclub.crawling.wechat.auto.room.acv.isWechatVideoType
import com.topviewclub.crawling.wechat.auto.room.acl.ACLimited
import com.topviewclub.crawling.wechat.auto.room.roomACLock
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.withLock
import kotlin.math.abs
import kotlin.random.Random

class ScanRequestCode : Action {

    private companion object {
        private const val EDIT_TEXT_ID = "com.tencent.mm:id/b4a"
        private val adminSet = hashSetOf("f939826156", "NemophilisitM", "wxid_072ql1ygqsya21")
    }

    override val actionName: String = "ScanRequestCode"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val root = service.rootInActiveWindow ?: return actionName

        val edit = root.findNodeOrNull {
            viewIdResourceName == EDIT_TEXT_ID && isEditable
        } ?: return actionName

        val ret = queryACLimitedAndACVideo()

        edit.text(ret)
        Thread.sleep(1000L)
        return "SendWechatMessage"
    }

    private fun queryACLimitedAndACVideo(): String {
        val ret: String
        val nameOfWechat = AutoChatOperationService.nameOfWechat
        val numberOfWechat = AutoChatOperationService.numberOfWechat
        val nowTime = System.currentTimeMillis()
        roomACLock.withLock {
            val aclList = ACLimitedDao.selectLimited(numberOfWechat)
            val type = AutoChatOperationService.requestWechatType
            if (isWechatVideoType(type)) {
                val url = AutoChatOperationService.videoURL
                AutoChatOperationService.videoURL = null
                val title = AutoChatOperationService.title
                AutoChatOperationService.title = ""
                if (url == null) {
                    ret = ":( 抱歉 @$nameOfWechat\n\n提取视频时出现网络波动，请稍后重试..."
                } else {
//                  val code = requireRequestCode(type)
                    val code = UUID.randomUUID().toString()
                    logI("[AAOS]", "Wechat = $numberOfWechat , Code = $type$code , Url = $url")
                    // 上传云端数据库
//                    sendToCloudDB(code,title,url)
//                    ACVideoDao.insertVideo(
//                        ACVideo(code, type, numberOfWechat, nameOfWechat, url, nowTime, title)
//                    )
                    if (aclList.isNotEmpty()) {
                        // 不为空列表，即该用户此前已经使用过此功能
                        val acl = aclList.first()
                        if (aclList.first().updateTime >= todayLong) {
                            // 今天内使用，增加请求次数
                            ACLimitedDao.updateLimited(
                                acl.copy(
                                    requestCount = acl.requestCount + 1,
                                    totalRequest = acl.totalRequest + 1,
                                    updateTime = nowTime
                                )
                            )
                            ret = if (adminSet.contains(numberOfWechat)) {
                                ":) 欢迎管理员 @$nameOfWechat\n\n" +
                                        "这是您今天的第${acl.requestCount + 1}次请求\n" +
                                        "提取码\n" +
                                        "$type$code\n\n" +
                                        "管理员测试信息\n$title\n$url"
                            } else {
                                ":) 欢迎 @$nameOfWechat\n\n" +
                                        "这是您今天的第${acl.requestCount + 1}次请求\n" +
                                        "提取码\n" +
                                        "$type$code"
                            }
                        } else {
                            // 今天未使用，清零
                            ACLimitedDao.updateLimited(
                                ACLimited(
                                    numberOfWechat = numberOfWechat,
                                    nameOfWechat = nameOfWechat,
                                    requestCount = 1,
                                    errorCount = 0,
                                    totalRequest = acl.totalRequest + 1,
                                    totalError = acl.totalError,
                                    updateTime = nowTime
                                )
                            )
                            ret = if (adminSet.contains(numberOfWechat)) {
                                ":) 欢迎管理员 @$nameOfWechat\n\n" +
                                        "这是您今天的第1次请求\n" +
                                        "提取码\n" +
                                        "$type$code\n\n" +
                                        "管理员测试信息\n$title\n$url"
                            } else {
                                ":) 欢迎 @$nameOfWechat\n\n" +
                                        "这是您今天的第1次请求\n" +
                                        "提取码\n" +
                                        "$type$code"
                            }
                        }
                    } else {
                        // 该用户是第一次使用，为其初始化一个元素
                        ACLimitedDao.insertLimited(
                            ACLimited(
                                numberOfWechat = numberOfWechat,
                                nameOfWechat = nameOfWechat,
                                requestCount = 1,
                                errorCount = 0,
                                totalRequest = 1,
                                totalError = 0,
                                updateTime = nowTime
                            )
                        )
                        ret = if (adminSet.contains(numberOfWechat)) {
                            ":) 欢迎管理员 @$nameOfWechat\n\n" +
                                    "这是您今天的第1次请求\n" +
                                    "提取码\n" +
                                    "$type$code\n\n" +
                                    "管理员测试信息\n$title\n$url"
                        } else {
                            ":) 欢迎 @$nameOfWechat\n\n" +
                                    "这是您今天的第1次请求\n" +
                                    "提取码\n" +
                                    "$type$code"
                        }
                    }
                }
            } else {
                if (aclList.isNotEmpty()) {
                    // 记录该用户的违规行为
                    val acl = aclList.first()
                    ACLimitedDao.updateLimited(
                        acl.copy(
                            errorCount = acl.errorCount + 1,
                            totalError = acl.totalError + 1,
                            updateTime = nowTime
                        )
                    )
                } else {
                    // 增加用户，记录违规行为
                    ACLimitedDao.insertLimited(
                        ACLimited(
                            numberOfWechat = numberOfWechat,
                            nameOfWechat = nameOfWechat,
                            requestCount = 0,
                            errorCount = 1,
                            totalRequest = 0,
                            totalError = 1,
                            updateTime = nowTime
                        )
                    )
                }
                ret = if (adminSet.contains(numberOfWechat)) {
                    ":) 欢迎管理员 @$nameOfWechat\n\n未发现可识别的信息\n\n技术统计\n" +
                            "成功任务数 | ${TaskStat.successfulTaskList.value!!.size}\n" +
                            "完成任务数 | ${TaskStat.completedTaskList.value!!.size}\n" +
                            "执行成功率 | ${TaskStat.successfulRate.value} %\n\n" +
                            "启动时间 | \n${formatterYMD.format(TaskStat.startDate)}\n" +
                            "当前时间 | \n${formatterYMD.format(Date())}"
                } else {
                    ":( 抱歉 @$nameOfWechat\n\n艾小新好像不认识这个东西噢\n未发现可识别的消息"
                }
            }
        }
        return ret
    }

    private fun requireRequestCode(type: String): Long {
        var code: Long
        do {
            code = abs(Random.nextLong())
            // Long.MAX_VALUE = 922_3372_0368_5477_5807L
        } while (code <= 99_9999_9999_9999_9999L || ACVideoDao.selectVideo(code, type).isNotEmpty())
        return code
    }



    private val formatterYMD = SimpleDateFormat(
        "yyyy年M月d日HH:mm:ss.SSS",
        Locale.getDefault()
    )

    private val todayLong: Long
        get() {
            // 今天的日期
            val todayStr = "${
                formatterYMD.format(Date()).run {
                    substring(0, indexOf("日") + 1)
                }
            }00:00:00.000"
            // 获取今天的第一毫秒，返回空则获取当前时间
            return formatterYMD.parse(todayStr)?.time ?: System.currentTimeMillis()
        }

}
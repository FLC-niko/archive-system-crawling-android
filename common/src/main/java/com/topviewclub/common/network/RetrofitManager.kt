package com.topviewclub.common.network

import com.topviewclub.common.base.appContext
import com.topviewclub.common.log.logE
import com.topviewclub.common.log.logHEART
import com.topviewclub.common.log.logRabbit
import com.topviewclub.common.util.RestartApp.RestartAppTool
import kotlinx.coroutines.*
import okhttp3.Response
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.create
import retrofit2.http.*
import java.util.concurrent.LinkedBlockingQueue

private var ipAddress = ""

//暴露给全局的设备mac地址，方便更改，直接写死
// 目前使用的两个设备码分别为
// "C6P7HYDY9LYHAQON"  ,  "M7Z5HABUMJT8BUO7"
const val  macAddress = "C6P7HYDY9LYHAQON"

fun setIp(ip: String) {
    ipAddress = ip
    service = Retrofit.Builder()
        .baseUrl("http://$ip:8080/")
        .build()
        .create()
}
// 主机
private lateinit var service: hostApi

// nacos注册中心
private val nacosRegistService: registerApi = Retrofit.Builder()
    .baseUrl("http://10.21.23.91:8848/")
    .build()
    .create()

// 云数据库
private val cloudDBService : singleVideoApi = Retrofit.Builder()
    .baseUrl("http://10.21.23.226/")
    .build()
    .create()



/**
 * 注册中心api
 */
private interface registerApi {
    @POST("nacos/v1/ns/instance")
    suspend fun register(
        @Query("serviceName") serviceName: String = "android",
        @Query("ip") ip: String = macAddress,
        @Query("port") port: Long = 8080,
        @Query("weight") weight: Long = 1,
        @Query("namespaceId") namespaceId: String = "d8756d63-afa1-474f-b5bf-be336b82e666",
        @Query("metadata") metadata: String = """
        {
             "name": "${macAddress}",
             "macAddress": "${macAddress}",
             "preserved.heart.beat.timeout":60000,
             "preserved.ip.delete.timeout": 120000
            
        }
    """.trimIndent()


    ): ResponseBody

    @PUT("nacos/v1/ns/instance/beat")
    suspend fun registerBeat(
        @Query("serviceName") serviceName: String = "Android",
        @Query("ip") ip: String = macAddress,
        @Query("port") port: Long = 8080,
        @Query("namespaceId") namespaceId: String = "d8756d63-afa1-474f-b5bf-be336b82e666"
    ): ResponseBody
}

/**
 * 申请大数据保活的api
 */
private interface hostApi {

    @GET("error")
    suspend fun report(
        @Query("message") msg: String,
        @Query("tag") tag: String,
        @Query("device_code") device_code: String? = macAddress
    ): ResponseBody

    @GET("acvideo")
    suspend fun acVideo(
        @Query("message") msg: String,
        @Query("device_code") device_code: String? = macAddress
    ): ResponseBody

    @GET("heartbeat")
    suspend fun heartbeat(
        @Query("device_code") device_code: String? = macAddress
    ): ResponseBody
}

/**
 * 单个视频云数据库api
 */
private interface singleVideoApi {
    @GET("/api/android/getAndroidCode")
    suspend fun cloudDB(
        @Query("code")code:String,
        @Query("title")title:String,
        @Query("url")url:String,
    ):ResponseBody
}

private data class ErrorMsg(
    val prefix: String,
    val msg: String,
    val tag: String,
    val cause: Throwable?
)

private val errorMsgQueue = LinkedBlockingQueue<ErrorMsg>()

private val acMsgQueue = LinkedBlockingQueue<String>()

private fun sendMessageToHostError(errorMsg: ErrorMsg) {
    with(errorMsg) {
        sendMessageToHostError(prefix, msg, tag, cause)
    }
}


/**
 * 向注册中心注册
 * */
@OptIn(DelicateCoroutinesApi::class)
fun sendToNacosRegister() {
    GlobalScope.launch(Dispatchers.IO) {
        if (runCatching {
                nacosRegistService.register()
            }.onFailure {
                it.printStackTrace()
            }.isFailure) {
            logRabbit("send To NacosRegister Fail ")
        }
    }
}

/**
 * 向注册中心发送心跳
 * */
@OptIn(DelicateCoroutinesApi::class)
fun sendToNacosRegisterBeat() {
    GlobalScope.launch(Dispatchers.IO) {
        runCatching {
            logHEART("Register", "Try")
            val response = nacosRegistService.registerBeat()
            val json = response.string()
            val root = JSONObject(json)
            val code = root.getInt("code")
            if(code == 20404){
                sendToNacosRegister()
            }
            logHEART("Register", "Success")
        }.onFailure {
            it.printStackTrace()
            logHEART("Register", "$it")
        }
    }
}

/**
 * 向主机发送信息
 * */
@OptIn(DelicateCoroutinesApi::class)
fun sendMessageToHostError(
    prefix: String,
    msg: String,
    tag: String,
    cause: Throwable? = null
) {
    if (cause != null) {
        logE(prefix, cause.message ?: "Error")

//        //新增，如果是报错，尝试在这一步重启服务
//        if(msg == "SNR" || cause.message == "NET"){
//        }
    }
    if (!::service.isInitialized) return
    // 只要进程仍然存活，发送即可
    GlobalScope.launch(Dispatchers.IO) {
        if (runCatching {
                service.report("[$prefix] $msg", tag)
            }.onFailure {
                it.printStackTrace()
            }.isFailure) {
            errorMsgQueue.add(ErrorMsg(prefix, msg, tag, cause))
        }
    }
}

/**
 * 重启app
 */
@OptIn(DelicateCoroutinesApi::class)
fun restartApp(
    prefix: String,
    msg: String,
    tag: String,
    cause: Throwable? = null
) {
    if (cause != null) {
        logE(prefix, cause.message ?: "Error")
        if (runCatching {
                RestartAppTool.restartApp(appContext)
            }.onFailure {
                it.printStackTrace()
            }.isFailure) {
            errorMsgQueue.add(ErrorMsg(prefix, msg, tag, cause))
        }
    }
    if (!::service.isInitialized) return
}


@OptIn(DelicateCoroutinesApi::class)
fun sendMessageToHostErrorOnce(
    prefix: String,
    msg: String,
    tag: String,
    cause: Throwable? = null
) {
    if (cause != null) {
        logE(prefix, cause.message ?: "Error")
    }
    if (!::service.isInitialized) return
    // 只要进程仍然存活，发送即可
    GlobalScope.launch(Dispatchers.IO) {
        runCatching { service.report("[$prefix] $msg", tag) }.onFailure {
            it.printStackTrace()
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
fun sendACVideoToHostAC(msg: String) {
    if (!::service.isInitialized) {
        return
    }
    // 只要进程仍然存活，发送即可
    GlobalScope.launch(Dispatchers.IO) {
        if (runCatching { service.acVideo(msg) }.onFailure {
                it.printStackTrace()
            }.isFailure) {
            acMsgQueue.add(msg)
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
fun sendHeartBeatToHostHeartbeatOnce() {
    GlobalScope.launch(Dispatchers.IO) {
        if (::service.isInitialized) {
            // 发送心跳
            do {
                val r = runCatching {
                    logHEART("Host", "Try")
                    service.heartbeat()
                    logHEART("Host", "Success")
                }.onFailure {
                    it.printStackTrace()
                    logHEART("Host", "$it")
                }.isFailure
            } while (r)
            var s = errorMsgQueue.size
            for (i in 0 until s) {
                errorMsgQueue.poll()?.let { msg ->
                    sendMessageToHostError(msg)
                }
            }
            s = acMsgQueue.size
            for (i in 0 until s) {
                acMsgQueue.poll()?.let { msg ->
                    sendACVideoToHostAC(msg)
                }
            }
        } else {
            logHEART("AAHEARTBEATOS", "No Ip Address")
        }
    }
}
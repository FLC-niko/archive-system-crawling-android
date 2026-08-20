package com.topviewclub.common.wirebare

import com.topviewclub.common.log.logE
import com.topviewclub.common.log.logRabbit
import org.github.kokomi.wirebare.interceptor.HttpRequestUrlInterceptor
import org.github.kokomi.wirebare.interceptor.RequestInterceptor

class WechatVideoUrlInterceptor private constructor(
    private val onRequestKV: (Pair<String, String>) -> Unit
) : HttpRequestUrlInterceptor() {

    companion object {
        internal fun factory(onRequestKV: (Pair<String, String>) -> Unit): () -> RequestInterceptor {
            return { WechatVideoUrlInterceptor(onRequestKV) }
        }
    }

    override fun onRequest(url: String) {
        transferKV(url)?.let {
            onRequestKV(it)
        }
    }

    /**.
     * 过滤微信视频号视频 url
     *
     * 根据逆向分析，只有 4 个参数是必须的，分别是
     *
     * cdnkey 视频的key，用于识别视频，这个参数对于同一个视频应该是永远不会改变的
     *
     * cdntoken 一个提取的令牌，未知功能，这个参数应该是会随着时间过期的
     *
     * tokenidx 未知功能，但好像每次都是传的都是 1
     *
     * X-snsvideoflag 这个参数是是视频的质量，改为 xV0 则为视频的最高质量（前提是原来也是xV开头），
     * 若传其它数值，则根据传的数值决定质量，
     * 传的值越大，质量越高，
     * 若不存在目标质量，则默认最高质量
     *
     * */
    private fun transferKV(url: String): Pair<String, String>? {
        // 取得 url
        // 视频号视频 url 的主机地址的后缀，前缀不一定可能会改变，所以只能判断后缀

//        logRabbit("url : $url")

        val hostSuffixIndex = url.lastIndexOf(".video.qq.com/")
        if (hostSuffixIndex == -1) return null


        val stoDownloadIndex = url.indexOf("stodownload?")
        if (stoDownloadIndex == -1) return null

        val prefix = "https://finder${url.substring(hostSuffixIndex, stoDownloadIndex + 11)}"
        // 解析 url 的参数列表
        val paramIndex = url.indexOf("?")
        if (paramIndex == -1) return null


        val params = hashMapOf<String, String>()
        url.substring(paramIndex + 1).split("&").forEach {
            val keyValue = it.split("=")
            if (keyValue.size >= 2) {
                val key = keyValue[0]
                val value = StringBuilder().apply {
                    for (i in 1 until keyValue.size) {
                        append(keyValue[i])
                    }
                }.toString()
                params[key] = value
            }
        }
        val cdnKey = params["cdnkey"] ?: return null
        val cdnToken = params["cdntoken"] ?: return null
        val tokenIdx = params["tokenidx"] ?: return null
//        var snsFlag = params["X-snsvideoflag"] ?: return null
//        if (snsFlag.indexOf("xV") != -1) {
//        snsFlag = "xV0"
//        }
        val target = "$prefix?" +
                "X-snsvideoflag=xV0" +
                "&cdnkey=$cdnKey&cdntoken=$cdnToken&tokenidx=$tokenIdx"

        logRabbit("target : $target")
        return cdnKey to target
    }

}
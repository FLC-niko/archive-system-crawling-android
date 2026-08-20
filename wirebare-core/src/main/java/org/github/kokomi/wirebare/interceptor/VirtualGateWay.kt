package org.github.kokomi.wirebare.interceptor

import org.github.kokomi.wirebare.common.WireBare
import org.github.kokomi.wirebare.common.WireBareConfiguration
import java.nio.ByteBuffer

/**
 * 虚拟网关
 * */
class VirtualGateWay(
    configuration: WireBareConfiguration
) {

    private val requestChain: RequestChain = RequestChain(
        mutableListOf<RequestInterceptor>().apply {
            // 请求头格式化拦截器
            add(RequestHeaderParseInterceptor())
            configuration.requestInterceptorFactories.forEach {
                add(it.create())
            }
        }
    )

    //    private val responseChain =

    internal fun onRequest(buffer: ByteBuffer) {
        requestChain.startProcessing(buffer)
    }

}
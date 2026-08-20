package com.topviewclub.common.wirebare

import android.app.Activity
import org.github.kokomi.wirebare.common.WireBare

fun Activity.prepareProxy(): Boolean {
    return WireBare.prepareProxy(this, 2222)
}

fun startWechatVideoProxy(onRequestKV: (Pair<String, String>) -> Unit) {
    WireBare.startProxy {
        mtu = 8192
        proxyAddress = "10.1.10.1" to 32
        addRoutes("0.0.0.0" to 0)
        addAllowedApplications("com.tencent.mm")
        addRequestInterceptors(WechatVideoUrlInterceptor.factory(onRequestKV))
    }
}

fun handlePrepareResult(
    requestCode: Int,
    resultCode: Int,
    result: (Boolean) -> Unit
) {
    WireBare.handlePrepareResult(requestCode, resultCode, 2222, result)
}

fun stopWireBareProxy() {
    WireBare.stopProxy()
}
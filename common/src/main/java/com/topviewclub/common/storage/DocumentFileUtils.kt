package com.topviewclub.common.storage

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract

object DocumentFileUtils {

    //转换至 TreeDocumentFile 的路径
    fun changeToUri(path: String) =
        "content://com.android.externalstorage.documents/tree/primary%3A" +
                path.replace("/storage/emulated/0/", "").replace("/", "%2F")

    //获取指定目录的权限
    fun startForPermission(path: String, context: Activity) {
        require(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                !path.contains("/Android/data/"),
        ) {
            "Android 11+ 不允许通过 ACTION_OPEN_DOCUMENT_TREE 授权 Android/data 子目录"
        }
        val uri = changeToUriInternal(path)
        val parse = Uri.parse(uri)
        val intent = Intent("android.intent.action.OPEN_DOCUMENT_TREE")
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, parse)
        }
        context.startActivityForResult(intent, 1024)
    }

    private fun changeToUriInternal(path: String): String {
        var path1 = path
        if (path1.endsWith("/")) {
            path1 = path1.substring(0, path.length - 1)
        }
        return "content://com.android.externalstorage.documents/tree/primary%3AAndroid%2Fdata/document/primary%3A" +
                path1.replace("/storage/emulated/0/", "").replace("/", "%2F")
    }

}

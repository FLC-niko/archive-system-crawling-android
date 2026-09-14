package com.topviewclub.common.storage

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import com.topviewclub.common.bean.TaskResultType
import com.topviewclub.common.log.logE
import com.topviewclub.common.log.logI
import com.topviewclub.common.network.sendMessageToHostError
import com.topviewclub.common.util.className
import com.topviewclub.common.util.defaultOutputDirectory
import java.io.File
import java.io.OutputStream

sealed class QrImagePersistenceResult {
    data class Success(val uri: Uri) : QrImagePersistenceResult()

    data class Failure(
        val code: String,
        val message: String,
    ) : QrImagePersistenceResult()
}


/**
 * 更新二维码
 * */
fun Context.updateQRCode(tag: String? = null) {
    runCatching {
        val file = File(defaultOutputDirectory().path + "/Code")
        if (!file.exists()) return

        val fileList = file.listFiles()?.toList() ?: return
        val imagePath = mutableListOf<String>()
        val imageMime = mutableListOf<String>()

        for (i in fileList.indices) {
            val f = fileList[i]
            val path = f.path
            val b = BitmapFactory.decodeFile(path)
            if (b != null) {
                val mimeType = "image/" + if (f.extension == "jpg") "jpeg" else f.extension
                val values = ContentValues()
                values.put(MediaStore.Images.Media.DATA, path)
                values.put(
                    MediaStore.Images.Media.MIME_TYPE,
                    mimeType
                )
                contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                imagePath.add(path)
                imageMime.add(mimeType)
            }
        }

        if (imagePath.isNotEmpty()) {
            MediaScannerConnection.scanFile(
                this,
                imagePath.toTypedArray(),
                imageMime.toTypedArray(),
                null
            )
        }

    }.onFailure {
        sendMessageToHostError(
            className,
            TaskResultType.UPDATE_PICTURE_EXCEPTION,
            tag ?: "",
            it
        )
    }
}

fun Context.updateQR(tag: String? = null, qr: String?): QrImagePersistenceResult {
    if (qr.isNullOrBlank()) {
        return QrImagePersistenceResult.Failure(
            code = "MISSING_QR_DATA",
            message = "公众号任务缺少二维码 Base64 数据",
        )
    }

    val decodedByte = try {
        Base64.decode(qr, Base64.DEFAULT)
    } catch (e: Exception) {
        return QrImagePersistenceResult.Failure(
            code = "INVALID_BASE64_DATA",
            message = "二维码 Base64 解码失败: ${e.message}",
        )
    }
    val bitmap = BitmapFactory.decodeByteArray(decodedByte, 0, decodedByte.size)
        ?: return QrImagePersistenceResult.Failure(
            code = "INVALID_QR_IMAGE",
            message = "二维码内容不是 Android 可识别的图片",
        )

    var imageUri: Uri? = null
    return try {
        val simpleName = "QRCode_${tag ?: System.currentTimeMillis()}.jpg"
        val mimeType = "image/jpeg"
        val relativeLocation = Environment.DIRECTORY_PICTURES + File.separator + "aaos"

        // 1. 同时物理写入公共 Pictures/aaos 目录及双开用户 999 对应目录，保证双开应用与系统相册均能直接读取该文件
        val targetDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "aaos")
        if (!targetDir.exists()) targetDir.mkdirs()
        val targetFile = File(targetDir, simpleName)
        targetFile.outputStream().use { out ->
            val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            check(compressed) { "二维码 JPEG 物理文件编码失败" }
        }

        val dualTargetDir = File("/storage/emulated/999/Pictures", "aaos")
        if (!dualTargetDir.exists()) dualTargetDir.mkdirs()
        val dualTargetFile = File(dualTargetDir, simpleName)
        runCatching {
            targetFile.copyTo(dualTargetFile, overwrite = true)
        }

        // 2. 插入 MediaStore，显式设置时间戳确保位于相册最前
        val nowSec = System.currentTimeMillis() / 1000
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, simpleName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.DATA, targetFile.absolutePath)
            put(MediaStore.MediaColumns.DATE_ADDED, nowSec)
            put(MediaStore.MediaColumns.DATE_MODIFIED, nowSec)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeLocation)
            }
        }
        val contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        imageUri = contentResolver.insert(contentUri, contentValues)
            ?: throw IllegalStateException("MediaStore insert 返回 null")

        // 3. 通知 MediaScanner 对物理文件建立完整索引
        MediaScannerConnection.scanFile(
            this,
            arrayOf(targetFile.absolutePath, dualTargetFile.absolutePath),
            arrayOf(mimeType, mimeType),
        ) { path, uri ->
            logI("QR", "MediaScanner indexed $path to $uri")
        }

        // 4. 发送广播让多用户/应用双开 (User 999) 媒体库同步发现此文件
        runCatching {
            sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(targetFile)))
            sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(dualTargetFile)))
        }

        QrImagePersistenceResult.Success(imageUri)
    } catch (e: Exception) {
        imageUri?.let { uri -> runCatching { contentResolver.delete(uri, null, null) } }
        logE(
            "QR", "AnalysisJson QR Exception   " +
                    "Cause = ${e.cause} , Message = ${e.message}"
        )
        QrImagePersistenceResult.Failure(
            code = "QR_PERSIST_FAILED",
            message = "二维码写入系统相册失败: ${e.message}",
        )
    } finally {
        bitmap.recycle()
    }
}

fun Context.deleteAllPhotos(folderName: String): Boolean {
    // 1. 物理删除 User 0 和 User 999 目录下所有历史二维码图片，彻底根除脏文件
    listOf(
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), folderName),
        File("/storage/emulated/999/Pictures", folderName),
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), folderName),
    ).forEach { dir ->
        if (dir.exists()) {
            dir.listFiles()?.forEach { f ->
                runCatching { f.delete() }
            }
        }
    }

    // 2. 清理 MediaStore 记录，使用 runCatching 避免权限异常阻断流程
    return runCatching {
        val contentResolver = contentResolver
        val contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%${folderName}%")
        contentResolver.query(contentUri, null, selection, selectionArgs, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                val imageUri = ContentUris.withAppendedId(contentUri, id)
                runCatching { contentResolver.delete(imageUri, null, null) }
            }
        }
        true
    }.onFailure {
        it.printStackTrace()
        logE(
            "QR", "Delete Photos Exception   " +
                    "Cause = ${it.cause} , Message = ${it.message}"
        )
    }.getOrDefault(false)
}




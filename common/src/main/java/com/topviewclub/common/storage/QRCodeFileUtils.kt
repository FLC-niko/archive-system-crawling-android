package com.topviewclub.common.storage

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import com.topviewclub.common.bean.TaskResultType
import com.topviewclub.common.log.logE
import com.topviewclub.common.network.sendMessageToHostError
import com.topviewclub.common.util.className
import com.topviewclub.common.util.defaultOutputDirectory
import java.io.File
import java.io.OutputStream


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

fun Context.updateQR(tag: String? = null, qr: String?) {
    runCatching {
        if (qr == null) return
        val decodedByte = Base64.decode(qr, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(decodedByte, 0, decodedByte.size)
        val fileName = defaultOutputDirectory().path + "/QRCode" + tag
        val mimeType = "image/jpeg"
        val relativeLocation = Environment.DIRECTORY_PICTURES + File.separator + "aaos"
//        val relativeLocation = Environment.DIRECTORY_PICTURES

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeLocation)
            }
        }
        val contentUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.getContentUri("external")
        }

        val imageUri = contentResolver.insert(contentUri, contentValues)

        val outputStream: OutputStream? = imageUri?.let { contentResolver.openOutputStream(it) }
        try {
            outputStream?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (imageUri != null) {
            MediaScannerConnection.scanFile(
                this,
                arrayOf(imageUri.toString()),
                null,
                null
            )

            // 新增，为了尝试能够在微信中扫描到传过来的图片
//            val values = ContentValues().apply {
//                put(MediaStore.Images.Media.DATA, imageUri.toString())
//                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
//                put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
//            }
//            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)
        }


    }.onFailure {
        logE(
            "QR", "AnalysisJson QR Exception   " +
                    "Cause = ${it.cause} , Message = ${it.message}"
        )
    }

}

fun Context.deleteAllPhotos(folderName: String) {
    runCatching {
        val contentResolver = contentResolver
        val contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%${folderName}%")
        contentResolver.query(contentUri, null, selection, selectionArgs, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                val imageUri = ContentUris.withAppendedId(contentUri, id)
                contentResolver.delete(imageUri, null, null)
            }
        }

    }.onFailure {
        it.printStackTrace()
        logE(
            "QR", "Delete Photos Exception   " +
                    "Cause = ${it.cause} , Message = ${it.message}"
        )
    }

}





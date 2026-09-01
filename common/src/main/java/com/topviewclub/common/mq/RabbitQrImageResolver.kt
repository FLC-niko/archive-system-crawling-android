package com.topviewclub.common.mq

import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

private const val MAX_QR_IMAGE_BYTES = 10 * 1024 * 1024

class RabbitQrResolutionException(
    val code: String,
    val retryable: Boolean,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/**
 * 解析 V2 二维码引用。当前协议没有提供对象存储 base URL 或签名接口，因此只接受
 * objectKey 本身就是 HTTP(S) URL 的任务；不再静默复用设备上的旧二维码。
 */
suspend fun resolveRabbitQrImage(reference: QrImageRef?): String = withContext(Dispatchers.IO) {
    val source = validateRabbitQrReference(reference)
    val requiredReference = requireNotNull(reference)

    val bytes = downloadQrImage(source, requiredReference.sizeBytes)
    if (bytes.size.toLong() != requiredReference.sizeBytes) {
        throw RabbitQrResolutionException(
            code = "QR_SIZE_MISMATCH",
            retryable = true,
            message = "二维码实际大小与 qrImage.sizeBytes 不一致",
        )
    }

    val actualSha256 = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(Locale.US, it.toInt() and 0xff) }
    if (!actualSha256.equals(requiredReference.sha256.trim(), ignoreCase = true)) {
        throw RabbitQrResolutionException(
            code = "QR_SHA256_MISMATCH",
            retryable = true,
            message = "二维码 SHA-256 校验失败",
        )
    }
    if (BitmapFactory.decodeByteArray(bytes, 0, bytes.size) == null) {
        throw RabbitQrResolutionException(
            code = "INVALID_QR_IMAGE",
            retryable = false,
            message = "下载内容不是 Android 可识别的图片",
        )
    }
    Base64.encodeToString(bytes, Base64.NO_WRAP)
}

internal fun validateRabbitQrReference(reference: QrImageRef?): String {
    if (reference == null) {
        throw RabbitQrResolutionException(
            code = "MISSING_QR_IMAGE",
            retryable = false,
            message = "V2 公众号任务缺少 qrImage，无法安全确认采集账号",
        )
    }
    if (!reference.contentType.startsWith("image/", ignoreCase = true)) {
        throw RabbitQrResolutionException(
            code = "INVALID_QR_CONTENT_TYPE",
            retryable = false,
            message = "qrImage.contentType 不是图片类型",
        )
    }
    if (reference.sizeBytes <= 0 || reference.sizeBytes > MAX_QR_IMAGE_BYTES) {
        throw RabbitQrResolutionException(
            code = "INVALID_QR_SIZE",
            retryable = false,
            message = "qrImage.sizeBytes 非法或超过 10 MiB",
        )
    }

    val source = reference.objectKey.trim()
    if (!source.startsWith("https://") && !source.startsWith("http://")) {
        throw RabbitQrResolutionException(
            code = "UNRESOLVED_QR_OBJECT_KEY",
            retryable = false,
            message = "qrImage.objectKey 不是可下载 URL，协议需要补充对象存储解析地址",
        )
    }
    return source
}

private fun downloadQrImage(source: String, declaredSize: Long): ByteArray {
    val connection = (URL(source).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 20_000
        instanceFollowRedirects = true
        requestMethod = "GET"
    }
    try {
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            throw RabbitQrResolutionException(
                code = "QR_DOWNLOAD_HTTP_$responseCode",
                retryable = responseCode >= 500 || responseCode == 408 || responseCode == 429,
                message = "二维码下载失败: HTTP $responseCode",
            )
        }
        val contentLength = connection.contentLengthLong
        if (contentLength > MAX_QR_IMAGE_BYTES || contentLength > declaredSize) {
            throw RabbitQrResolutionException(
                code = "QR_DOWNLOAD_TOO_LARGE",
                retryable = false,
                message = "二维码响应大小超过协议声明或 10 MiB 限制",
            )
        }

        connection.inputStream.use { input ->
            val output = ByteArrayOutputStream(declaredSize.coerceAtMost(MAX_QR_IMAGE_BYTES.toLong()).toInt())
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_QR_IMAGE_BYTES || total.toLong() > declaredSize) {
                    throw RabbitQrResolutionException(
                        code = "QR_DOWNLOAD_TOO_LARGE",
                        retryable = false,
                        message = "二维码响应大小超过协议声明或 10 MiB 限制",
                    )
                }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    } catch (e: RabbitQrResolutionException) {
        throw e
    } catch (e: Exception) {
        throw RabbitQrResolutionException(
            code = "QR_DOWNLOAD_FAILED",
            retryable = true,
            message = "二维码下载异常: ${e.message}",
            cause = e,
        )
    } finally {
        connection.disconnect()
    }
}

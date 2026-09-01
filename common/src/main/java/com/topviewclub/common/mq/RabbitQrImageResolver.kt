package com.topviewclub.common.mq

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Locale

private const val MIN_QR_IMAGE_BYTES = 1L
private const val MAX_QR_IMAGE_BYTES = 1048576L // 1MB = 1024 * 1024
private const val REQUIRED_TRANSPORT = "INLINE_BASE64"

class RabbitQrResolutionException(
    val code: String,
    val retryable: Boolean,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/**
 * 解析并校验 V2 二维码内联 Base64 数据。
 * 协议规则：
 * 1. transport 固定为 INLINE_BASE64。
 * 2. 从 dataBase64 使用标准 Base64 解码，不带 data:image/...;base64, 前缀。
 * 3. 不再提供或兼容 objectKey、OBJECT_REFERENCE，无需访问 MinIO。
 * 4. 解码后校验：
 *    - 大小为 1～1048576 字节
 *    - 实际大小等于 sizeBytes
 *    - SHA-256 等于 sha256
 *    - contentType 必须以 image/ 开头
 * 5. objectKey、非 INLINE_BASE64 transport 或摘要不一致作为不可重试的协议错误处理 (retryable = false)。
 */
suspend fun resolveRabbitQrImage(reference: QrImageRef?): String = withContext(Dispatchers.Default) {
    val requiredReference = validateRabbitQrReference(reference)

    val base64Data = requiredReference.dataBase64?.trim().orEmpty()
    if (base64Data.isEmpty()) {
        throw RabbitQrResolutionException(
            code = "MISSING_QR_DATA",
            retryable = false,
            message = "V2 任务 qrImage 缺少 dataBase64 数据",
        )
    }

    if (base64Data.startsWith("data:", ignoreCase = true)) {
        throw RabbitQrResolutionException(
            code = "INVALID_BASE64_DATA",
            retryable = false,
            message = "dataBase64 不能包含 data:image 前缀，必须是标准 Base64 编码",
        )
    }

    val bytes = try {
        decodeBase64Safe(base64Data)
    } catch (e: Exception) {
        throw RabbitQrResolutionException(
            code = "INVALID_BASE64_DATA",
            retryable = false,
            message = "dataBase64 解码失败: ${e.message}",
            cause = e,
        )
    }

    if (bytes.size.toLong() != requiredReference.sizeBytes) {
        throw RabbitQrResolutionException(
            code = "QR_SIZE_MISMATCH",
            retryable = false,
            message = "二维码实际大小 (${bytes.size}) 与 qrImage.sizeBytes (${requiredReference.sizeBytes}) 不一致",
        )
    }

    if (bytes.size.toLong() < MIN_QR_IMAGE_BYTES || bytes.size.toLong() > MAX_QR_IMAGE_BYTES) {
        throw RabbitQrResolutionException(
            code = "INVALID_QR_SIZE",
            retryable = false,
            message = "二维码实际大小 (${bytes.size}) 超出 1～1048576 字节范围限制",
        )
    }

    val actualSha256 = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(Locale.US, it.toInt() and 0xff) }
    if (!actualSha256.equals(requiredReference.sha256.trim(), ignoreCase = true)) {
        throw RabbitQrResolutionException(
            code = "QR_SHA256_MISMATCH",
            retryable = false,
            message = "二维码 SHA-256 校验失败: 期望 ${requiredReference.sha256.trim()}, 实际 $actualSha256",
        )
    }

    // 返回标准无换行的 Base64 字符串供后续系统落盘相册
    encodeBase64Safe(bytes)
}

internal fun validateRabbitQrReference(reference: QrImageRef?): QrImageRef {
    if (reference == null) {
        throw RabbitQrResolutionException(
            code = "MISSING_QR_IMAGE",
            retryable = false,
            message = "V2 任务缺少 qrImage",
        )
    }

    if (!reference.objectKey.isNullOrBlank()) {
        throw RabbitQrResolutionException(
            code = "UNSUPPORTED_QR_OBJECT_KEY",
            retryable = false,
            message = "协议已调整：不再支持 objectKey/OBJECT_REFERENCE，必须使用 INLINE_BASE64",
        )
    }

    if (reference.transport != REQUIRED_TRANSPORT) {
        throw RabbitQrResolutionException(
            code = "UNSUPPORTED_QR_TRANSPORT",
            retryable = false,
            message = "qrImage.transport 必须为 $REQUIRED_TRANSPORT，当前为 ${reference.transport}",
        )
    }

    if (!reference.contentType.startsWith("image/", ignoreCase = true)) {
        throw RabbitQrResolutionException(
            code = "INVALID_QR_CONTENT_TYPE",
            retryable = false,
            message = "qrImage.contentType 必须以 image/ 开头，当前为 ${reference.contentType}",
        )
    }

    if (reference.sizeBytes < MIN_QR_IMAGE_BYTES || reference.sizeBytes > MAX_QR_IMAGE_BYTES) {
        throw RabbitQrResolutionException(
            code = "INVALID_QR_SIZE",
            retryable = false,
            message = "qrImage.sizeBytes (${reference.sizeBytes}) 超出 1～1048576 字节范围限制",
        )
    }

    if (reference.sha256.isBlank()) {
        throw RabbitQrResolutionException(
            code = "MISSING_QR_SHA256",
            retryable = false,
            message = "qrImage.sha256 不能为空",
        )
    }

    return reference
}

internal fun decodeBase64Safe(input: String): ByteArray {
    return runCatching {
        java.util.Base64.getDecoder().decode(input)
    }.getOrElse {
        android.util.Base64.decode(input, android.util.Base64.DEFAULT)
    }
}

internal fun encodeBase64Safe(bytes: ByteArray): String {
    return runCatching {
        java.util.Base64.getEncoder().encodeToString(bytes)
    }.getOrElse {
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }
}

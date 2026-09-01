package com.topviewclub.common.mq

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RabbitQrImageResolverTest {

    private val validSampleRef = QrImageRef(
        transport = "INLINE_BASE64",
        dataBase64 = "cXItYnl0ZXM=",
        sha256 = "256c71a1a5c9904f339078276acc7b056c2e307b81c70f4c5d44a910f8bfb639",
        contentType = "image/png",
        sizeBytes = 8,
    )

    @Test
    fun acceptsValidInlineBase64QrImage() = runBlocking {
        val result = resolveRabbitQrImage(validSampleRef)
        assertEquals("cXItYnl0ZXM=", result)
    }

    @Test
    fun rejectsObjectKey() {
        try {
            validateRabbitQrReference(
                validSampleRef.copy(objectKey = "https://example.com/qr.png")
            )
            fail("应拒绝包含 objectKey 的请求")
        } catch (e: RabbitQrResolutionException) {
            assertEquals("UNSUPPORTED_QR_OBJECT_KEY", e.code)
            assertFalse(e.retryable)
        }
    }

    @Test
    fun rejectsNonInlineBase64Transport() {
        try {
            validateRabbitQrReference(
                validSampleRef.copy(transport = "OBJECT_REFERENCE")
            )
            fail("应拒绝非 INLINE_BASE64 transport")
        } catch (e: RabbitQrResolutionException) {
            assertEquals("UNSUPPORTED_QR_TRANSPORT", e.code)
            assertFalse(e.retryable)
        }
    }

    @Test
    fun rejectsInvalidContentType() {
        try {
            validateRabbitQrReference(
                validSampleRef.copy(contentType = "application/json")
            )
            fail("应拒绝非 image/ 开头的 contentType")
        } catch (e: RabbitQrResolutionException) {
            assertEquals("INVALID_QR_CONTENT_TYPE", e.code)
            assertFalse(e.retryable)
        }
    }

    @Test
    fun rejectsSizeMismatch() = runBlocking {
        try {
            resolveRabbitQrImage(
                validSampleRef.copy(sizeBytes = 16)
            )
            fail("应拒绝 sizeBytes 不匹配")
        } catch (e: RabbitQrResolutionException) {
            assertEquals("QR_SIZE_MISMATCH", e.code)
            assertFalse(e.retryable)
        }
    }

    @Test
    fun rejectsOutOfRangeSize() {
        try {
            validateRabbitQrReference(
                validSampleRef.copy(sizeBytes = 0)
            )
            fail("应拒绝小于 1 字节的大小")
        } catch (e: RabbitQrResolutionException) {
            assertEquals("INVALID_QR_SIZE", e.code)
            assertFalse(e.retryable)
        }

        try {
            validateRabbitQrReference(
                validSampleRef.copy(sizeBytes = 1048577) // > 1MB
            )
            fail("应拒绝大于 1MB (1048576 字节) 的大小")
        } catch (e: RabbitQrResolutionException) {
            assertEquals("INVALID_QR_SIZE", e.code)
            assertFalse(e.retryable)
        }
    }

    @Test
    fun rejectsSha256Mismatch() = runBlocking {
        try {
            resolveRabbitQrImage(
                validSampleRef.copy(sha256 = "1111111111111111111111111111111111111111111111111111111111111111")
            )
            fail("应拒绝 SHA-256 不匹配")
        } catch (e: RabbitQrResolutionException) {
            assertEquals("QR_SHA256_MISMATCH", e.code)
            assertFalse(e.retryable)
        }
    }

    @Test
    fun rejectsDataUriPrefix() = runBlocking {
        try {
            resolveRabbitQrImage(
                validSampleRef.copy(dataBase64 = "data:image/png;base64,cXItYnl0ZXM=")
            )
            fail("应拒绝包含 data:image 前缀的数据")
        } catch (e: RabbitQrResolutionException) {
            assertEquals("INVALID_BASE64_DATA", e.code)
            assertFalse(e.retryable)
        }
    }

    @Test
    fun rejectsMissingQr() {
        try {
            validateRabbitQrReference(null)
            fail("应拒绝 null 的二维码引用")
        } catch (e: RabbitQrResolutionException) {
            assertEquals("MISSING_QR_IMAGE", e.code)
            assertFalse(e.retryable)
        }
    }
}

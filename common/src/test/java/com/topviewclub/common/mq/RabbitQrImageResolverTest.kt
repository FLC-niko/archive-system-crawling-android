package com.topviewclub.common.mq

import org.junit.Assert.assertEquals
import org.junit.Test

class RabbitQrImageResolverTest {
    @Test
    fun acceptsHttpImageReference() {
        val source = validateRabbitQrReference(
            QrImageRef(
                objectKey = "https://objects.example.com/qr.png",
                sha256 = "00",
                contentType = "image/png",
                sizeBytes = 128,
            ),
        )

        assertEquals("https://objects.example.com/qr.png", source)
    }

    @Test
    fun rejectsOpaqueObjectKeyInsteadOfReusingOldQr() {
        try {
            validateRabbitQrReference(
                QrImageRef(
                    objectKey = "tenant/qr/account.png",
                    sha256 = "00",
                    contentType = "image/png",
                    sizeBytes = 128,
                ),
            )
            throw AssertionError("opaque object key should be rejected")
        } catch (e: RabbitQrResolutionException) {
            assertEquals("UNRESOLVED_QR_OBJECT_KEY", e.code)
        }
    }

    @Test
    fun rejectsMissingQrInsteadOfReusingOldQr() {
        try {
            validateRabbitQrReference(null)
            throw AssertionError("missing qr should be rejected")
        } catch (e: RabbitQrResolutionException) {
            assertEquals("MISSING_QR_IMAGE", e.code)
        }
    }
}

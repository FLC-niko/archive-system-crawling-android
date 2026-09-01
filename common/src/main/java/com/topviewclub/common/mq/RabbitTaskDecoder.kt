package com.topviewclub.common.mq

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.topviewclub.common.bean.GzhAutoFromBackend

sealed class RabbitTaskMessage {
    abstract val eventId: String
    abstract val workflowId: String
    abstract val idempotencyKey: String

    data class V2(val task: GzhAutoBta) : RabbitTaskMessage() {
        override val eventId: String = task.eventId
        override val workflowId: String = task.workflowId
        override val idempotencyKey: String = task.idempotencyKey
    }

    data class Legacy(val task: GzhAutoFromBackend) : RabbitTaskMessage() {
        override val eventId: String = "legacy-${task.correlationId}"
        override val workflowId: String = task.correlationId
        override val idempotencyKey: String = "$workflowId:BTA"
    }

    fun asV2(): GzhAutoBta = when (this) {
        is V2 -> task
        is Legacy -> RabbitV2Envelope(
            schemaVersion = RABBIT_SCHEMA_VERSION,
            eventId = eventId,
            workflowId = workflowId,
            causationId = null,
            idempotencyKey = idempotencyKey,
            messageType = "COMMAND",
            tenantCode = "legacy",
            mediaType = "WECHAT_OFFICIAL_ACCOUNT",
            operation = "AUTO_CRAWL",
            occurredAt = "",
            producer = RabbitProducer("legacy-adapter"),
            business = RabbitBusiness(task.jobId, task.correlationId),
            payload = GzhAutoBtaPayload(
                account = GzhAccount(task.gzhName, entryUrl = task.url),
                captureWindow = CaptureWindow(
                    startsAt = task.tempTimeStamp.getOrElse(0) { "" },
                    endsAt = task.tempTimeStamp.getOrElse(1) { "" },
                ),
            ),
        )
    }
}

class RabbitTaskFormatException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

object RabbitTaskDecoder {
    // 显式写出泛型，避免 Kotlin typealias 在 Gson TypeToken 中被擦除。
    private val v2Type = object : TypeToken<RabbitV2Envelope<GzhAutoBtaPayload>>() {}.type
    private val legacyType = object : TypeToken<GzhAutoFromBackend>() {}.type

    /** 对其他 feature module 隐藏 Gson，避免把 common 的实现依赖泄露到调用方。 */
    fun decode(body: ByteArray): RabbitTaskMessage = try {
        decodeInternal(body, Gson())
    } catch (e: RabbitTaskFormatException) {
        throw e
    } catch (e: Exception) {
        throw RabbitTaskFormatException(e.message ?: "RabbitMQ 任务格式非法", e)
    }

    private fun decodeInternal(body: ByteArray, gson: Gson): RabbitTaskMessage {
        val root = JsonParser.parseString(body.toString(Charsets.UTF_8))
        require(root.isJsonObject) { "RabbitMQ 任务必须是 JSON 对象" }
        val rootObject = root.asJsonObject

        return if (rootObject.has("payload")) {
            requireNonBlankString(rootObject, "schemaVersion", "V2 任务")
            require(rootObject.get("schemaVersion").asString == RABBIT_SCHEMA_VERSION) {
                "不支持的 RabbitMQ schemaVersion"
            }
            requireNonBlankString(rootObject, "eventId", "V2 任务")
            requireNonBlankString(rootObject, "workflowId", "V2 任务")
            requireNonBlankString(rootObject, "idempotencyKey", "V2 任务")
            val payload = requireObject(rootObject, "payload", "V2 任务")
            val account = requireObject(payload, "account", "V2 任务")
            requireNonBlankString(account, "name", "V2 任务 account")
            val captureWindow = requireObject(payload, "captureWindow", "V2 任务")
            requireNonBlankString(captureWindow, "startsAt", "V2 任务 captureWindow")
            requireNonBlankString(captureWindow, "endsAt", "V2 任务 captureWindow")
            val business = requireObject(rootObject, "business", "V2 任务")
            require(business.has("jobId") && !business.get("jobId").isJsonNull) {
                "V2 任务 business 缺少 jobId"
            }

            val task: GzhAutoBta = gson.fromJson(root, v2Type)
            RabbitTaskMessage.V2(task)
        } else {
            requireNonBlankString(rootObject, "gzhName", "旧版任务")
            requireNonBlankString(rootObject, "correlationId", "旧版任务")
            val timestamps = rootObject.get("tempTimeStamp")
            require(timestamps != null && timestamps.isJsonArray && timestamps.asJsonArray.size() >= 2) {
                "旧版任务缺少完整 tempTimeStamp"
            }
            RabbitTaskMessage.Legacy(gson.fromJson(root, legacyType))
        }
    }

    private fun requireObject(root: JsonObject, fieldName: String, messageType: String): JsonObject {
        val value = root.get(fieldName)
        require(value != null && value.isJsonObject) { "$messageType 缺少 $fieldName" }
        return value.asJsonObject
    }

    private fun requireNonBlankString(root: JsonObject, fieldName: String, messageType: String): String {
        val value = root.get(fieldName)
        require(
            value != null && value.isJsonPrimitive && !value.isJsonNull && value.asJsonPrimitive.isString,
        ) { "${messageType}缺少 $fieldName" }
        return value.asString.also { require(it.isNotBlank()) { "${messageType}缺少 $fieldName" } }
    }
}

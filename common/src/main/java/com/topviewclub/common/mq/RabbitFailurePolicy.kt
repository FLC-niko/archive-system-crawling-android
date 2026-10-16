package com.topviewclub.common.mq

internal enum class RabbitFailureDisposition {
    RETRY,
    DEAD_LETTER,
    PARK,
}

internal fun rabbitFailureDisposition(
    topology: RabbitTopology,
    deliveryQueue: String,
    retryCount: Int,
): RabbitFailureDisposition = when {
    retryCount < MAX_TASK_RETRY -> RabbitFailureDisposition.RETRY
    !topology.brokerManagedTopology && deliveryQueue == topology.deadLetterQueue ->
        RabbitFailureDisposition.PARK
    else -> RabbitFailureDisposition.DEAD_LETTER
}

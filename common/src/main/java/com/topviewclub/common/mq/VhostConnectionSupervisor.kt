package com.topviewclub.common.mq

import com.topviewclub.common.log.logRabbit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

enum class VhostConnectionState {
    DISCONNECTED,
    CONNECTING,
    ACTIVE,
}

/** 每个 vhost 独立退避重连，一个 vhost 失败不会阻塞其他 vhost。 */
class VhostConnectionSupervisor(
    private val bindings: List<VhostBinding>,
    private val initialRetryIntervalMs: Long,
    private val maxRetryIntervalMs: Long,
) {
    data class VhostBinding(
        val name: String,
        val virtualHost: String,
        val manager: RabbitMQClientManager,
    )

    private val states = ConcurrentHashMap<String, VhostConnectionState>()
    private val jobs = mutableListOf<Job>()
    private val started = AtomicBoolean(false)

    init {
        bindings.forEach { states[it.name] = VhostConnectionState.DISCONNECTED }
    }

    fun start(scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) return
        bindings.forEach { binding -> jobs += scope.launch { supervise(binding) } }
    }

    fun snapshot(): Map<String, VhostConnectionState> = states.toMap()

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    private suspend fun supervise(binding: VhostBinding) {
        var retryDelay = initialRetryIntervalMs.coerceAtLeast(1_000L)
        while (kotlin.coroutines.coroutineContext.isActive && started.get()) {
            if (binding.manager.isConnected()) {
                if (states.put(binding.name, VhostConnectionState.ACTIVE) != VhostConnectionState.ACTIVE) {
                    logRabbit("vhost=${binding.name} 已恢复")
                }
                retryDelay = initialRetryIntervalMs.coerceAtLeast(1_000L)
                delay(retryDelay)
                continue
            }

            states[binding.name] = VhostConnectionState.CONNECTING
            try {
                binding.manager.connect(virtualHost = binding.virtualHost)
                states[binding.name] = VhostConnectionState.ACTIVE
                retryDelay = initialRetryIntervalMs.coerceAtLeast(1_000L)
                logRabbit("vhost=${binding.name} 连接及拓扑就绪")
                delay(retryDelay)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                states[binding.name] = VhostConnectionState.DISCONNECTED
                logRabbit("vhost=${binding.name} 连接失败: ${e.message}; ${retryDelay / 1000}s 后重试")
                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(maxRetryIntervalMs.coerceAtLeast(retryDelay))
            }
        }
        states[binding.name] = VhostConnectionState.DISCONNECTED
    }
}

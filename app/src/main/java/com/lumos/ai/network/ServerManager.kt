package com.lumos.ai.network

/** Tracks whether llama-server is reachable, without exposing raw Termux to the user. */
class ServerManager(private val api: LlamaApiClient) {

    sealed class Status {
        object Checking : Status()
        object Online : Status()
        data class Offline(val detail: String) : Status()
    }

    fun check(serverUrl: String): Status {
        return try {
            if (api.isHealthy(serverUrl)) Status.Online
            else Status.Offline("llama-server not reachable at $serverUrl")
        } catch (e: Exception) {
            Status.Offline(e.message ?: "unknown error")
        }
    }
}

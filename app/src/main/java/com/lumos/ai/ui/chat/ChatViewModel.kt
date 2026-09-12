package com.lumos.ai.ui.chat

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.lumos.ai.data.ConversationRepository
import com.lumos.ai.data.LumosSettings
import com.lumos.ai.data.SettingsRepository
import com.lumos.ai.data.db.LumosDatabase
import com.lumos.ai.data.db.MessageEntity
import com.lumos.ai.network.LlamaApiClient
import com.lumos.ai.network.ServerManager
import com.lumos.ai.service.GenerationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call

data class ChatUiState(
    val conversationId: Long = -1L,
    val title: String = "New Chat",
    val messages: List<MessageEntity> = emptyList(),
    val input: String = "",
    val generating: Boolean = false,
    val serverOnline: Boolean = false,
    val serverDetail: String = "checking…",
    val error: String? = null
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ConversationRepository(LumosDatabase.get(app))
    private val settingsRepo = SettingsRepository(app)
    private val api = LlamaApiClient()
    private val serverManager = ServerManager(api)

    private val _ui = MutableStateFlow(ChatUiState())
    val ui: StateFlow<ChatUiState> = _ui.asStateFlow()

    private var currentCall: Call? = null
    private var serverJob: Job? = null
    private var observeJob: Job? = null

    // Lets the "Stop" action in the generation notification cancel the
    // in-flight stream even while the app itself is backgrounded.
    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = stop()
    }
    private var stopReceiverRegistered = false

    init {
        startServerPolling()
        val filter = IntentFilter(GenerationService.ACTION_STOP)
        val ctx = getApplication<Application>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ctx.registerReceiver(stopReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            ctx.registerReceiver(stopReceiver, filter)
        }
        stopReceiverRegistered = true
    }

    fun loadConversation(id: Long) {
        if (id <= 0) return
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repo.observeMessages(id).collect { msgs ->
                _ui.update { s ->
                    val conv = runCatching { repo.getConversation(id) }.getOrNull()
                    s.copy(conversationId = id, messages = msgs, title = conv?.title ?: "Chat")
                }
            }
        }
    }

    private fun startServerPolling() {
        serverJob?.cancel()
        serverJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val s = settingsRepo.settings.first()
                when (val st = serverManager.check(s.serverUrl)) {
                    is ServerManager.Status.Online ->
                        _ui.update { it.copy(serverOnline = true, serverDetail = "local AI online") }
                    is ServerManager.Status.Offline ->
                        _ui.update {
                            it.copy(serverOnline = false, serverDetail = "offline — start llama-server in Termux")
                        }
                    else -> {}
                }
                delay(5000)
            }
        }
    }

    fun onInputChange(v: String) = _ui.update { it.copy(input = v, error = null) }

    fun send() {
        val text = _ui.value.input.trim()
        if (text.isEmpty() || _ui.value.generating) return

        viewModelScope.launch {
            val settings = settingsRepo.settings.first()
            if (!_ui.value.serverOnline) {
                _ui.update { it.copy(error = "Local AI is offline. Start llama-server (see Settings).") }
                return@launch
            }

            var convId = _ui.value.conversationId
            if (convId <= 0) {
                val title = if (text.length > 26) text.take(26) + "…" else text
                convId = repo.createConversation(title, settings.modelName, settings.systemPrompt)
                _ui.update { it.copy(conversationId = convId, title = title) }
                loadConversation(convId)
            }

            repo.touchConversation(convId, null)
            val userMsg = MessageEntity(conversationId = convId, role = "user", content = text)
            repo.addMessage(userMsg)

            val placeholder = MessageEntity(conversationId = convId, role = "assistant", content = "")
            val assistantId = repo.addMessage(placeholder)

            _ui.update { it.copy(input = "", generating = true, error = null) }
            streamInto(convId, assistantId, settings)
        }
    }

    fun regenerate() {
        val state = _ui.value
        val lastAssistant = state.messages.lastOrNull { it.role == "assistant" } ?: return
        if (state.generating) return

        viewModelScope.launch {
            val settings = settingsRepo.settings.first()
            repo.deleteMessage(lastAssistant.id)
            val placeholder = MessageEntity(
                conversationId = state.conversationId, role = "assistant", content = ""
            )
            val assistantId = repo.addMessage(placeholder)
            _ui.update { it.copy(generating = true, error = null) }
            streamInto(state.conversationId, assistantId, settings)
        }
    }

    fun stop() {
        currentCall?.cancel()
        currentCall = null
        stopGenerationService()
        _ui.update { it.copy(generating = false) }
    }

    fun deleteMessage(id: Long) {
        viewModelScope.launch {
            repo.deleteMessage(id)
            _ui.update { s -> s.copy(messages = s.messages.filterNot { it.id == id }) }
        }
    }

    private suspend fun streamInto(convId: Long, assistantId: Long, settings: LumosSettings) {
        startGenerationService()
        val history = repo.listMessages(convId)
        val buffer = StringBuilder()

        currentCall = api.streamChat(
            settings = settings,
            history = history.filter { it.role != "assistant" || it.id != assistantId },
            onToken = { token ->
                buffer.append(token)
                updateMessageInState(assistantId, buffer.toString())
            },
            onDone = {
                persistFinal(assistantId, buffer.toString())
                currentCall = null
                stopGenerationService()
            },
            onError = { err ->
                persistFinal(assistantId, buffer.toString())
                currentCall = null
                stopGenerationService()
                _ui.update { it.copy(generating = false, error = err) }
            }
        )
        // generating flag cleared in persistFinal / onError
    }

    /**
     * Starts (or leaves running) the foreground service for the duration of a
     * generation. Android's background execution limits can throttle or kill
     * the streaming network callback once the app leaves the foreground;
     * the service + its notification keeps the process alive and tells the
     * user why. The service does no work itself — this ViewModel still owns
     * the OkHttp call.
     */
    private fun startGenerationService() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, GenerationService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }
    }

    private fun stopGenerationService() {
        val ctx = getApplication<Application>()
        ctx.stopService(Intent(ctx, GenerationService::class.java))
    }

    private fun updateMessageInState(id: Long, content: String) {
        _ui.update { s ->
            s.copy(
                messages = s.messages.map { if (it.id == id) it.copy(content = content) else it }
            )
        }
    }

    private fun persistFinal(id: Long, content: String) {
        viewModelScope.launch {
            val existing = _ui.value.messages.firstOrNull { it.id == id }
            val final = (existing?.copy(
                content = content,
                tokenCount = content.length / 4
            ) ?: MessageEntity(id = id, conversationId = _ui.value.conversationId,
                role = "assistant", content = content))
            repo.updateMessage(final)
            _ui.update { it.copy(generating = false) }
        }
    }

    override fun onCleared() {
        currentCall?.cancel()
        serverJob?.cancel()
        observeJob?.cancel()
        stopGenerationService()
        if (stopReceiverRegistered) {
            runCatching { getApplication<Application>().unregisterReceiver(stopReceiver) }
        }
    }

    companion object {
        fun factory(app: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return ChatViewModel(app) as T
            }
        }
    }
}

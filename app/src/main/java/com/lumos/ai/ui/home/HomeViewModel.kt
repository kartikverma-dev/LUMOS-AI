package com.lumos.ai.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.lumos.ai.data.ConversationRepository
import com.lumos.ai.data.SettingsRepository
import com.lumos.ai.data.db.ConversationEntity
import com.lumos.ai.data.db.LumosDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ConversationRepository(LumosDatabase.get(app))
    private val settingsRepo = SettingsRepository(app)

    private val _conversations = MutableStateFlow<List<ConversationEntity>>(emptyList())
    val conversations: StateFlow<List<ConversationEntity>> = _conversations.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeConversations().collect { _conversations.value = it }
        }
    }

    fun createConversation(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val s = settingsRepo.settings.first()
            val id = repo.createConversation("New Chat", s.modelName, s.systemPrompt)
            onCreated(id)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.deleteConversation(id) }
    }

    companion object {
        fun factory(app: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return HomeViewModel(app) as T
            }
        }
    }
}

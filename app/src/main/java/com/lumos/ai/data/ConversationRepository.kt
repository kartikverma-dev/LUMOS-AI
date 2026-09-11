package com.lumos.ai.data

import com.lumos.ai.data.db.ConversationEntity
import com.lumos.ai.data.db.LumosDatabase
import com.lumos.ai.data.db.MessageEntity
import kotlinx.coroutines.flow.Flow

class ConversationRepository(private val db: LumosDatabase) {

    fun observeConversations(): Flow<List<ConversationEntity>> =
        db.conversationDao().observeAll()

    fun observeMessages(convId: Long): Flow<List<MessageEntity>> =
        db.messageDao().observeFor(convId)

    suspend fun getConversation(id: Long): ConversationEntity? =
        db.conversationDao().get(id)

    suspend fun createConversation(title: String, model: String, systemPrompt: String): Long =
        db.conversationDao().insert(
            ConversationEntity(title = title, model = model, systemPrompt = systemPrompt)
        )

    suspend fun touchConversation(id: Long, title: String?) {
        val c = db.conversationDao().get(id) ?: return
        db.conversationDao().update(
            c.copy(
                title = title ?: c.title,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteConversation(id: Long) {
        db.messageDao().deleteFor(id)
        db.conversationDao().delete(id)
    }

    suspend fun listMessages(convId: Long): List<MessageEntity> =
        db.messageDao().listFor(convId)

    suspend fun addMessage(m: MessageEntity): Long = db.messageDao().insert(m)

    suspend fun updateMessage(m: MessageEntity) = db.messageDao().update(m)

    suspend fun deleteMessage(id: Long) = db.messageDao().deleteById(id)
}

    package com.lumos.ai.data.db

    import androidx.room.Dao
    import androidx.room.Insert
    import androidx.room.Query
import androidx.room.Update
    import kotlinx.coroutines.flow.Flow

    @Dao
    interface ConversationDao {
        @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
        fun observeAll(): Flow<List<ConversationEntity>>

        @Query("SELECT * FROM conversations WHERE id = :id")
        suspend fun get(id: Long): ConversationEntity?

        @Insert
        suspend fun insert(c: ConversationEntity): Long

        @Update
        suspend fun update(c: ConversationEntity)

        @Query("DELETE FROM conversations WHERE id = :id")
        suspend fun delete(id: Long)
    }

    @Dao
    interface MessageDao {
        @Query("SELECT * FROM messages WHERE conversationId = :convId ORDER BY timestamp, id")
        fun observeFor(convId: Long): Flow<List<MessageEntity>>

        @Query("SELECT * FROM messages WHERE conversationId = :convId ORDER BY timestamp, id")
        suspend fun listFor(convId: Long): List<MessageEntity>

        @Insert
        suspend fun insert(m: MessageEntity): Long

        @Update
        suspend fun update(m: MessageEntity)

        @Query("DELETE FROM messages WHERE conversationId = :convId")
        suspend fun deleteFor(convId: Long)

        @Query("DELETE FROM messages WHERE id = :id")
        suspend fun deleteById(id: Long)
    }

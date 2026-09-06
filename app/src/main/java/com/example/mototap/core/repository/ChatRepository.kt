package com.example.mototap.core.repository

import com.example.mototap.core.model.ChatMessage
import kotlinx.coroutines.flow.Flow

data class ChatSummary(
    val roomId: String,
    val partnerId: String,
    val lastMessage: ChatMessage,
    val otherUserName: String,
    val otherUserRole: String = "",
    val otherUserPhotoUrl: String = "",
    val garageName: String = "",
    val unread: Boolean = false,
) {
    /** Legacy alias used by older call sites / navigation. */
    val jobId: String get() = roomId
}

interface ChatRepository {
    fun observeMessages(roomId: String, currentUserId: String = ""): Flow<List<ChatMessage>>

    suspend fun sendMessage(
        roomId: String,
        senderId: String,
        text: String,
        senderName: String? = null,
        recipientName: String? = null,
    ): Result<Unit>

    suspend fun markAsRead(roomId: String, currentUserId: String): Result<Unit>

    fun observeChatSummaries(userId: String): Flow<List<ChatSummary>>

    suspend fun setTypingStatus(roomId: String, userId: String, isTyping: Boolean): Result<Unit>

    fun observeTypingStatus(roomId: String, currentUserId: String): Flow<Boolean>

    /** Removes only this user's inbox row (`chatPartners`); shared messages stay. */
    suspend fun deleteConversation(userId: String, partnerId: String): Result<Unit>

    /**
     * Ensures chat room docs + mutual `chatPartners` rows exist (website open-chat path).
     * Returns the canonical sorted room id.
     */
    suspend fun ensureConversation(
        userId: String,
        partnerId: String,
        participantNames: Map<String, String> = emptyMap(),
    ): Result<String>

    /**
     * Resolve a partner's display name the same way as the website:
     * chatPartners → room participantNames → publicProfiles → users.
     */
    suspend fun resolvePartnerName(
        currentUserId: String,
        partnerId: String,
        roomId: String = "",
    ): String

    /** Shop / garage name for mechanic or parts-dealer partners. */
    suspend fun resolvePartnerGarageName(partnerId: String): String

    suspend fun resolvePartnerRole(partnerId: String): String
}

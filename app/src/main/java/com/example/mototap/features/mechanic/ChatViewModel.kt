package com.example.mototap.features.mechanic

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mototap.core.model.ChatMessage
import com.example.mototap.core.repository.AuthRepository
import com.example.mototap.core.repository.ChatRepository
import com.example.mototap.core.util.ChatIds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val otherParticipantName: String = "User",
    val otherParticipantRole: String = "",
    val garageName: String = "",
    val isOtherTyping: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

class ChatViewModel(
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val jobId: String,
    private val currentUserId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val partnerId: String? =
        ChatIds.partnerIdFromRoomId(jobId, currentUserId)

    init {
        observeMessages()
        observeTypingStatus()
        fetchParticipantName()
        ensureRoom()
    }

    private fun ensureRoom() {
        val otherId = partnerId ?: return
        viewModelScope.launch {
            val myProfile = authRepository.getUserProfile(currentUserId)
            val resolvedPartnerName = chatRepository.resolvePartnerName(
                currentUserId,
                otherId,
                jobId,
            )
            chatRepository.ensureConversation(
                currentUserId,
                otherId,
                buildMap {
                    myProfile?.name?.takeIf { it.isNotBlank() }?.let { put(currentUserId, it) }
                    resolvedPartnerName.takeIf { it.isNotBlank() && it != "User" }
                        ?.let { put(otherId, it) }
                },
            )
            if (resolvedPartnerName.isNotBlank() && resolvedPartnerName != "User") {
                _uiState.update { it.copy(otherParticipantName = resolvedPartnerName) }
            }
            applyPartnerShopDetails(otherId)
        }
    }

    private fun fetchParticipantName() {
        val otherId = partnerId ?: return
        viewModelScope.launch {
            val name = chatRepository.resolvePartnerName(currentUserId, otherId, jobId)
            if (name.isNotBlank()) {
                _uiState.update { it.copy(otherParticipantName = name) }
            }
            applyPartnerShopDetails(otherId)
        }
    }

    private suspend fun applyPartnerShopDetails(otherId: String) {
        val role = chatRepository.resolvePartnerRole(otherId)
        val garageName = chatRepository.resolvePartnerGarageName(otherId)
        _uiState.update {
            it.copy(
                otherParticipantRole = role,
                garageName = garageName,
            )
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            chatRepository.observeMessages(jobId, currentUserId).collect { messages ->
                _uiState.update { it.copy(messages = messages) }

                val unreadCount = messages.count { !it.read && it.senderId != currentUserId }
                if (unreadCount > 0) {
                    Log.d("ChatViewModel", "Marking $unreadCount messages as read in room $jobId")
                    chatRepository.markAsRead(jobId, currentUserId)
                }
            }
        }
    }

    private fun observeTypingStatus() {
        viewModelScope.launch {
            chatRepository.observeTypingStatus(jobId, currentUserId).collectLatest { isTyping ->
                _uiState.update { it.copy(isOtherTyping = isTyping) }
            }
        }
    }

    private var typingJob: Job? = null
    private var isCurrentlyTyping = false

    fun onInputChanged(text: String) {
        val isBlank = text.isBlank()

        if (!isBlank && !isCurrentlyTyping) {
            isCurrentlyTyping = true
            viewModelScope.launch {
                chatRepository.setTypingStatus(jobId, currentUserId, true)
            }
        }

        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            delay(2000)
            if (isCurrentlyTyping) {
                isCurrentlyTyping = false
                chatRepository.setTypingStatus(jobId, currentUserId, false)
            }
        }
    }

    fun setTypingStatus(isTyping: Boolean) {
        typingJob?.cancel()
        isCurrentlyTyping = isTyping
        viewModelScope.launch {
            chatRepository.setTypingStatus(jobId, currentUserId, isTyping)
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        setTypingStatus(false)

        val tempId = UUID.randomUUID().toString()
        val optimisticMessage = ChatMessage(
            id = tempId,
            senderId = currentUserId,
            text = text,
            timestampMillis = System.currentTimeMillis(),
            read = false
        )

        _uiState.update { state ->
            state.copy(messages = state.messages + optimisticMessage)
        }

        viewModelScope.launch {
            val myName = authRepository.getUserProfile(currentUserId)?.name
            val otherId = partnerId
            val otherName = otherId?.let {
                chatRepository.resolvePartnerName(currentUserId, it, jobId)
            } ?: _uiState.value.otherParticipantName
            val result = chatRepository.sendMessage(
                jobId,
                currentUserId,
                text,
                senderName = myName,
                recipientName = otherName,
            )
            if (result.isFailure) {
                _uiState.update { it.copy(error = "Failed to send message") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            chatRepository.setTypingStatus(jobId, currentUserId, false)
        }
    }
}

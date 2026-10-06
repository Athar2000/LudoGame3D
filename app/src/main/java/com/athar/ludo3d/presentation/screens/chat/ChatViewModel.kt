package com.athar.ludo3d.presentation.screens.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.ludo3d.domain.model.ChatMessage
import com.athar.ludo3d.domain.model.ChatMessageType
import com.athar.ludo3d.domain.repository.AuthRepository
import com.athar.ludo3d.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Added by Athar
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val currentUserId: String = "",
    val errorMessage: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    authRepository: AuthRepository
) : ViewModel() {

    private val roomId: String = checkNotNull(savedStateHandle["roomId"])

    private val _uiState = MutableStateFlow(ChatUiState(currentUserId = authRepository.currentUser()?.uid ?: ""))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        chatRepository.observeMessages(roomId)
            .onEach { messages -> _uiState.update { it.copy(messages = messages) } }
            .catch { e -> _uiState.update { it.copy(errorMessage = e.message) } }
            .launchIn(viewModelScope)
    }

    fun onDraftChange(value: String) = _uiState.update { it.copy(draft = value, errorMessage = null) }

    fun sendDraft() {
        val text = _uiState.value.draft
        if (text.isBlank()) return
        send(text, ChatMessageType.TEXT)
        _uiState.update { it.copy(draft = "") }
    }

    fun sendQuick(text: String) = send(text, ChatMessageType.QUICK)
    fun sendEmoji(emoji: String) = send(emoji, ChatMessageType.EMOJI)

    private fun send(text: String, type: ChatMessageType) {
        viewModelScope.launch {
            chatRepository.sendMessage(roomId, text, type)
                .onFailure { e -> _uiState.update { it.copy(errorMessage = e.message ?: "Could not send") } }
        }
    }
}

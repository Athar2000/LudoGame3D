package com.athar.ludo3d.presentation.sound

import androidx.lifecycle.ViewModel
import com.athar.ludo3d.domain.engine.SoundEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

// Added by Athar
// Thin bridge so Composables can reach the singleton SoundManager via hiltViewModel().
@HiltViewModel
class SoundViewModel @Inject constructor(private val soundManager: SoundManager) : ViewModel() {
    val soundEnabled: StateFlow<Boolean> = soundManager.enabled
    fun toggle() = soundManager.setEnabled(!soundManager.enabled.value)
    fun play(event: SoundEvent) = soundManager.play(event)
    fun play(sound: UiSound) = soundManager.play(sound)
}

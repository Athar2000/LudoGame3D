package com.athar.ludo3d.presentation.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.athar.ludo3d.R
import com.athar.ludo3d.domain.engine.SoundEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// Added by Athar
// Phase 9. SoundPool is the right tool for short effects: samples are decoded
// once into memory and can overlap (dice rattle + move blip) with very low
// latency and no per-play allocations, which suits mid-range phones (section 25).
// The on/off preference is stored in SharedPreferences so it survives restarts.
enum class UiSound { BUTTON_TAP }

@Singleton
class SoundManager @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences("ludo_prefs", Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_SOUND_ON, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids: Map<Any, Int> = mapOf(
        SoundEvent.DICE_ROLL to pool.load(context, R.raw.dice_roll, 1),
        SoundEvent.TOKEN_MOVE to pool.load(context, R.raw.token_move, 1),
        SoundEvent.CAPTURE to pool.load(context, R.raw.capture, 1),
        SoundEvent.TOKEN_HOME to pool.load(context, R.raw.token_home, 1),
        SoundEvent.YOUR_TURN to pool.load(context, R.raw.your_turn, 1),
        SoundEvent.WIN to pool.load(context, R.raw.win, 1),
        UiSound.BUTTON_TAP to pool.load(context, R.raw.button_tap, 1)
    )

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        prefs.edit().putBoolean(KEY_SOUND_ON, value).apply()
    }

    fun play(event: SoundEvent) = playId(ids[event])
    fun play(sound: UiSound) = playId(ids[sound])

    private fun playId(id: Int?) {
        if (id == null || !_enabled.value) return
        pool.play(id, 1f, 1f, 1, 0, 1f) // returns 0 if the sample hasn't finished loading yet; safe to ignore
    }

    private companion object { const val KEY_SOUND_ON = "sound_on" }
}

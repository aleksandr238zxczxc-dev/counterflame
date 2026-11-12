package com.example.counterflame

import android.media.AudioManager
import android.media.ToneGenerator

class SoundManager {
    private val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    fun shoot()      = safe { tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 60) }
    fun hit()        = safe { tg.startTone(ToneGenerator.TONE_PROP_ACK, 90) }
    fun hurt()       = safe { tg.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 180) }
    fun buy()        = safe { tg.startTone(ToneGenerator.TONE_PROP_PROMPT, 200) }
    fun die()        = safe { tg.startTone(ToneGenerator.TONE_CDMA_ABBR_REORDER, 250) }
    fun knifeSwing() = safe { tg.startTone(ToneGenerator.TONE_PROP_BEEP, 45) }
    fun knifeHit()   = safe { tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 120) }
    fun tradeOffer() = safe { tg.startTone(ToneGenerator.TONE_PROP_ACK, 150) }

    private inline fun safe(block: () -> Unit) { try { block() } catch (_: Exception) {} }
    fun release() = safe { tg.release() }
}

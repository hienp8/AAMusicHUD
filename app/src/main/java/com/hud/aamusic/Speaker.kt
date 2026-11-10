package com.hud.aamusic

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Đọc cảnh báo bằng giọng nói, tự hạ nhỏ nhạc (audio ducking) khi đọc. */
class Speaker(ctx: Context) : TextToSpeech.OnInitListener {
    private val am = ctx.getSystemService(AudioManager::class.java)
    private val tts = TextToSpeech(ctx, this)
    private var ready = false
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attrs)
        .build()

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts.setAudioAttributes(attrs)
        val r = tts.setLanguage(Locale("vi", "VN"))
        ready = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { am.abandonAudioFocusRequest(focus) }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { am.abandonAudioFocusRequest(focus) }
        })
    }

    fun speak(text: String) {
        if (!ready) return
        am.requestAudioFocus(focus)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "aa")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}

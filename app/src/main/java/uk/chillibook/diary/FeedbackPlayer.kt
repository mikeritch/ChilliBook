package uk.chillibook.diary

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View

internal enum class FeedbackCue { Select, Clear, Celebrate }

/** Foreground feedback owned by the host and used on the main thread. */
internal class FeedbackPlayer(context: Context) : AutoCloseable {
    private data class PendingSound(val sampleId: Int, val view: View, val deadline: Long)

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val readySamples = mutableSetOf<Int>()
    private var pendingSound: PendingSound? = null
    private val sounds = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
        .apply {
            setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) readySamples += sampleId
                val pending = pendingSound
                if (pending?.sampleId == sampleId) {
                    pendingSound = null
                    if (status == 0 && SystemClock.uptimeMillis() <= pending.deadline &&
                        pending.view.canProvideFeedback() && canPlaySound()
                    ) {
                        playSample(sampleId)
                    }
                }
            }
        }
    private val samples = mapOf(
        FeedbackCue.Select to sounds.load(context, R.raw.feedback_select, 1),
        FeedbackCue.Clear to sounds.load(context, R.raw.feedback_clear, 1),
        FeedbackCue.Celebrate to sounds.load(context, R.raw.feedback_celebrate, 1),
    )
    private var streamId = 0
    private var secondPulse: Runnable? = null
    private var closed = false

    fun play(view: View, cue: FeedbackCue, sound: Boolean, haptic: Boolean) {
        if (closed) return
        stop()
        if (!view.canProvideFeedback()) return
        if (sound && canPlaySound()) {
            val sampleId = samples.getValue(cue)
            if (sampleId in readySamples) {
                playSample(sampleId)
            } else if (sampleId != 0) {
                pendingSound = PendingSound(sampleId, view, SystemClock.uptimeMillis() + 150L)
            }
        }
        if (!haptic) return
        val lightTick = if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
            else HapticFeedbackConstants.CLOCK_TICK
        val tick = if (cue == FeedbackCue.Clear) lightTick else HapticFeedbackConstants.KEYBOARD_TAP
        val vibrated = view.performHapticFeedback(tick)
        if (cue == FeedbackCue.Celebrate && vibrated) {
            val pulse = Runnable {
                secondPulse = null
                if (view.canProvideFeedback()) view.performHapticFeedback(lightTick)
            }
            secondPulse = pulse
            handler.postDelayed(pulse, 90L)
        }
    }

    fun stop() {
        pendingSound = null
        secondPulse?.let(handler::removeCallbacks)
        secondPulse = null
        if (streamId != 0) sounds.stop(streamId)
        streamId = 0
    }

    override fun close() {
        if (closed) return
        stop()
        closed = true
        sounds.setOnLoadCompleteListener(null)
        sounds.release()
    }

    private fun View.canProvideFeedback(): Boolean = isAttachedToWindow && isShown &&
        windowVisibility == View.VISIBLE && hasWindowFocus()

    private fun canPlaySound(): Boolean {
        val manager = audioManager ?: return false
        return manager.ringerMode == AudioManager.RINGER_MODE_NORMAL &&
            !manager.isStreamMute(AudioManager.STREAM_MUSIC) &&
            manager.getStreamVolume(AudioManager.STREAM_MUSIC) > 0
    }

    private fun playSample(sampleId: Int) {
        streamId = sounds.play(sampleId, 1f, 1f, 1, 0, 1f)
    }
}

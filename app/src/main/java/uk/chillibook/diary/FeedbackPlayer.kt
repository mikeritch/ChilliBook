package uk.chillibook.diary

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
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

    fun play(view: View, cue: FeedbackCue, sound: Boolean, haptic: Boolean): Boolean {
        if (closed) return false
        stop()
        if (!view.canProvideFeedback()) return false
        if (sound && canPlaySound()) {
            val sampleId = samples.getValue(cue)
            if (sampleId in readySamples) {
                playSample(sampleId)
            } else if (sampleId != 0) {
                pendingSound = PendingSound(sampleId, view, SystemClock.uptimeMillis() + 150L)
            }
        }
        if (!haptic || !view.touchFeedbackEnabled()) return false

        val primary = when (cue) {
            FeedbackCue.Select -> HapticFeedbackConstants.VIRTUAL_KEY
            FeedbackCue.Clear -> HapticFeedbackConstants.CONTEXT_CLICK
            FeedbackCue.Celebrate -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
                else HapticFeedbackConstants.VIRTUAL_KEY
        }
        val accepted = view.performHapticFeedback(primary) ||
            (primary != HapticFeedbackConstants.VIRTUAL_KEY &&
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY))
        if (cue == FeedbackCue.Celebrate && accepted) {
            val pulse = Runnable {
                secondPulse = null
                if (view.canProvideFeedback() && view.touchFeedbackEnabled()) {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
            }
            secondPulse = pulse
            handler.postDelayed(pulse, 150L)
        }
        return accepted
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

    // Some Android versions report the request as sent even when touch feedback is off.
    @Suppress("DEPRECATION")
    private fun View.touchFeedbackEnabled(): Boolean =
        Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0

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

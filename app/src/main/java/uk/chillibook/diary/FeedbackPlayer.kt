package uk.chillibook.diary

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.RequiresApi
import kotlin.random.Random

internal enum class FeedbackCue { Select, Clear, Celebrate }

/** Foreground feedback owned by the host and used on the main thread. */
internal class FeedbackPlayer(context: Context) : AutoCloseable {
    private data class Sound(val sampleId: Int, val rate: Float, val celebration: Boolean)
    private data class PendingSound(val sound: Sound, val view: View, val deadline: Long)

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val vibrator = if (Build.VERSION.SDK_INT >= 33) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else null
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
                if (pending?.sound?.sampleId == sampleId) {
                    pendingSound = null
                    if (status == 0 && SystemClock.uptimeMillis() <= pending.deadline &&
                        pending.view.canProvideFeedback() && canPlaySound()
                    ) {
                        playSound(pending.sound)
                    }
                }
            }
        }
    private val samples = mapOf(
        FeedbackCue.Select to listOf(R.raw.feedback_select, R.raw.feedback_select_alt),
        FeedbackCue.Clear to listOf(R.raw.feedback_clear, R.raw.feedback_clear_alt),
        FeedbackCue.Celebrate to listOf(R.raw.feedback_celebrate),
    ).mapValues { (_, assets) -> assets.map { sounds.load(context, it, 1) }.filterNot { it == 0 } }
    private var streamId = 0
    private var lastEverydaySample: Int? = null
    private var celebrationEndsAt = 0L
    private var mediaVibrationRequested = false
    private var secondPulse: Runnable? = null
    private var closed = false

    fun play(
        view: View, cue: FeedbackCue, sound: Boolean, haptic: Boolean, preview: Boolean = false
    ): Boolean {
        if (closed) return false
        cancelSecondPulse()
        if (!view.canProvideFeedback()) {
            stopSound()
            return false
        }
        val now = SystemClock.uptimeMillis()
        val celebration = cue == FeedbackCue.Celebrate
        val celebrationPlaying = now < celebrationEndsAt ||
            pendingSound?.let { it.sound.celebration && now <= it.deadline } == true
        if (preview || celebration || !celebrationPlaying) {
            stopSound()
            if (sound && canPlaySound()) chooseSound(cue)?.let { next ->
                if (next.sampleId in readySamples) playSound(next)
                else pendingSound = PendingSound(next, view, now + 150L)
            }
        }
        if (!haptic) return false
        return if (Build.VERSION.SDK_INT >= 33) playMediaHaptic(cue) else playTouchHaptic(view, cue)
    }

    @RequiresApi(33)
    private fun playMediaHaptic(cue: FeedbackCue): Boolean {
        val motor = vibrator?.takeIf { it.hasVibrator() } ?: return false
        val effect = when (cue) {
            FeedbackCue.Select -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            FeedbackCue.Clear -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            FeedbackCue.Celebrate -> celebrationHaptic(motor)
        }
        motor.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA))
        mediaVibrationRequested = true
        return true
    }

    @RequiresApi(33)
    private fun celebrationHaptic(motor: Vibrator): VibrationEffect {
        val click = VibrationEffect.Composition.PRIMITIVE_CLICK
        val tick = VibrationEffect.Composition.PRIMITIVE_TICK
        if (!motor.areAllPrimitivesSupported(click, tick)) {
            return VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
        }
        return VibrationEffect.startComposition()
            .addPrimitive(click, .72f)
            .addPrimitive(tick, .45f, 120)
            .compose()
    }

    private fun playTouchHaptic(view: View, cue: FeedbackCue): Boolean {
        if (!view.touchFeedbackEnabled()) return false
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
        cancelSecondPulse()
        if (mediaVibrationRequested) {
            vibrator?.cancel()
            mediaVibrationRequested = false
        }
        stopSound()
    }

    private fun cancelSecondPulse() {
        secondPulse?.let(handler::removeCallbacks)
        secondPulse = null
    }

    private fun stopSound() {
        pendingSound = null
        celebrationEndsAt = 0L
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

    private fun chooseSound(cue: FeedbackCue): Sound? {
        val group = samples.getValue(cue)
        val available = group.filter { it in readySamples }.ifEmpty { group }
        val candidates = available.filterNot { it == lastEverydaySample }.ifEmpty { available }
        val sampleId = candidates.randomOrNull() ?: return null
        val celebration = cue == FeedbackCue.Celebrate
        val rate = if (celebration) 1f else Random.nextDouble(0.996, 1.004).toFloat()
        return Sound(sampleId, rate, celebration)
    }

    private fun playSound(sound: Sound) {
        streamId = sounds.play(sound.sampleId, 1f, 1f, 1, 0, sound.rate)
        if (streamId == 0) return
        if (sound.celebration) {
            celebrationEndsAt = SystemClock.uptimeMillis() + 680L
        } else {
            lastEverydaySample = sound.sampleId
        }
    }
}

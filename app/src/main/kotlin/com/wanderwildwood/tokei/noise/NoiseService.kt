package com.wanderwildwood.tokei.noise

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wanderwildwood.tokei.MainActivity
import com.wanderwildwood.tokei.R
import com.wanderwildwood.tokei.Times
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the sleep noise is doing, for the screen to show: nothing moves on it, it only says. */
data class NoiseState(val playing: Boolean = false, val preset: Preset = Preset.SOFT, val endsAt: Long = 0L)

/**
 * Plays the sleep noise from a foreground media-playback service, so it goes on with the screen
 * off. The noise is generated as it plays ([NoiseGen]) and written to an [AudioTrack]. It ends
 * on its own at the end of the chosen time, after a fade, and at once when an alarm rings
 * (Ringer.add) or anything else takes the audio.
 */
class NoiseService : Service() {

    private var thread: Thread? = null

    @Volatile
    private var stopping = false
    private var wake: PowerManager.WakeLock? = null
    private var focus: AudioFocusRequest? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            // Started with startForegroundService, it must reach the foreground before it stops.
            goForeground(Preset.SOFT, 0L)
            stopPlaying()
            stopSelf()
            return START_NOT_STICKY
        }
        val preset = Preset.of(intent?.getStringExtra(PRESET))
        val minutes = intent?.getIntExtra(MINUTES, 0) ?: 0
        val endsAt = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
        goForeground(preset, endsAt)
        stopPlaying()
        val audio = getSystemService(AudioManager::class.java)
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(ATTRS)
            .setOnAudioFocusChangeListener { change ->
                // A call, another player, an alarm: sleep noise gives way and does not return.
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    stopFromHere()
                }
            }
            .build()
            .also { audio.requestAudioFocus(it) }
        if (wake == null) {
            // Held only while it plays, and never past the longest it may go on.
            wake = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tokei:noise")
                .apply { acquire(MAX_MS + 60_000L) }
        }
        stopping = false
        state.value = NoiseState(true, preset, endsAt)
        thread = Thread({ play(preset, endsAt) }, "tokei-noise").also { it.start() }
        return START_NOT_STICKY
    }

    private fun play(preset: Preset, endsAt: Long) {
        val frames = NoiseGen.RATE / 4
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(ATTRS)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(NoiseGen.RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(
                    maxOf(
                        frames * 2,
                        AudioTrack.getMinBufferSize(NoiseGen.RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2,
                    ),
                )
                .build()
        } catch (e: Exception) {
            Log.e("tokei", "noise: no audio track", e)
            stopFromHere()
            return
        }
        val gen = NoiseGen(preset)
        val buf = ShortArray(NoiseGen.RATE / 20) // 50 ms a write
        val began = System.currentTimeMillis()
        val limit = began + MAX_MS
        try {
            track.play()
            while (!stopping) {
                val now = System.currentTimeMillis()
                val end = if (endsAt > 0L) minOf(endsAt, limit) else limit
                if (now >= end) break
                val gain = NoiseGen.fade(end - now, FADE_MS) * NoiseGen.fadeIn(now - began, FADE_IN_MS)
                gen.fill(buf, buf.size, gain)
                if (track.write(buf, 0, buf.size) < 0) break
            }
        } catch (e: Exception) {
            Log.e("tokei", "noise: stopped with an error", e)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
        if (!stopping) stopFromHere()
    }

    /** From the playing thread or a focus change: end the service. */
    private fun stopFromHere() {
        stopping = true
        android.os.Handler(mainLooper).post { stopSelf() }
    }

    private fun stopPlaying() {
        stopping = true
        thread?.let { if (it !== Thread.currentThread()) runCatching { it.join(500) } }
        thread = null
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
    }

    private fun goForeground(preset: Preset, endsAt: Long) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_noise), NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_NOISE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, NoiseService::class.java).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (endsAt > 0L) getString(R.string.noise_notif_until, Times.time(this, endsAt)) else getString(R.string.noise_notif_open)
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(getString(R.string.noise_notif_title, getString(presetName(preset))))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setContentIntent(open)
            .addAction(0, getString(R.string.noise_stop), stop)
            .build()
        startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }

    override fun onDestroy() {
        stopPlaying()
        wake?.let { if (it.isHeld) it.release() }
        wake = null
        state.value = NoiseState(false, state.value.preset, 0L)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "noise"
        private const val ID = 3
        private const val STOP = "com.wanderwildwood.tokei.noise.STOP"
        private const val PRESET = "preset"
        private const val MINUTES = "minutes"

        /** The longest it plays when set to run until stopped, so it is never left on for days. */
        const val MAX_MS = 12 * 60 * 60_000L
        const val FADE_MS = 15_000L
        const val FADE_IN_MS = 1_500L

        private val ATTRS = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        private val state = MutableStateFlow(NoiseState())

        /** Whether it plays in this process; false after the process died, which ends it too. */
        val current: StateFlow<NoiseState> = state

        fun presetName(p: Preset): Int = when (p) {
            Preset.SOFT -> R.string.noise_soft
            Preset.DEEP -> R.string.noise_deep
            Preset.FAN -> R.string.noise_fan
        }

        /** Starts it, or changes it to [preset] and [minutes] if it is playing. */
        fun start(context: Context, preset: Preset, minutes: Int) {
            context.startForegroundService(
                Intent(context, NoiseService::class.java).putExtra(PRESET, preset.key).putExtra(MINUTES, minutes),
            )
        }

        /** Stops it if it is playing; does nothing, and starts nothing, if it is not. */
        fun stop(context: Context) {
            if (!state.value.playing) return
            runCatching { context.stopService(Intent(context, NoiseService::class.java)) }
        }
    }
}

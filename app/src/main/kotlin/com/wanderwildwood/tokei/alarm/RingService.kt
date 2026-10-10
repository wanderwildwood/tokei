package com.wanderwildwood.tokei.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.wanderwildwood.tokei.data.Alarm
import com.wanderwildwood.tokei.data.Ring
import com.wanderwildwood.tokei.data.Settings
import com.wanderwildwood.tokei.data.Store

/**
 * What is ringing, kept in the settings so the ringing screen, the notification and the service
 * all read the same list. Adding to it or taking from it starts, updates or stops the service.
 */
object Ringer {

    fun add(context: Context, settings: Settings, rings: List<Ring>, now: Long) {
        val list = settings.ringing.filter { old -> rings.none { it.same(old) } } + rings
        settings.ringing = list
        settings.ringSince = now
        start(context)
        RingActivity.show(context)
    }

    /** Takes [ring] (matched by what it is, not when it was due) out of what is ringing. */
    fun remove(context: Context, ring: Ring) {
        val settings = Settings(context)
        val before = settings.ringing
        val after = before.filterNot { it.same(ring) }
        if (after.size == before.size) return
        settings.ringing = after
        Notifier.cancelRinging(context)
        if (after.isEmpty()) {
            settings.ringSince = 0
            context.stopService(Intent(context, RingService::class.java))
        } else {
            start(context)
        }
        Store.bump()
    }

    fun clear(context: Context) {
        val settings = Settings(context)
        settings.ringing = emptyList()
        settings.ringSince = 0
        Notifier.cancelRinging(context)
        context.stopService(Intent(context, RingService::class.java))
        Store.bump()
    }

    private fun start(context: Context) {
        Store.bump()
        try {
            context.startForegroundService(Intent(context, RingService::class.java))
        } catch (e: Exception) {
            // Not allowed to start from where this was called: the notification still rings
            // the screen, without the sound the service plays.
            Log.e("tokei", "could not start the ringing service", e)
            Notifier.ringing(context, Settings(context).ringing.lastOrNull() ?: return)
        }
    }
}

/**
 * Plays the sound and the vibration of what is ringing, and keeps its notification up, until it
 * is answered or [Settings.ringMinutes] go by. The sound is on the alarm stream, so a phone set to
 * silent still rings, and it loops until stopped (Fossify Clock's AlarmService, with its system
 * volume ramp replaced by the player's own volume, so the phone's alarm volume is never changed).
 *
 * During a call it rings quietly, at a tenth of its volume, and vibrates, so the call is not drowned.
 */
class RingService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var playing: Ring? = null
    private var focus: AudioFocusRequest? = null
    private var wake: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopAfter = Runnable { Engine.unanswered(this) }
    private var riseStart = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val settings = Settings(this)
        val ringing = settings.ringing
        val top = ringing.lastOrNull()
        if (top == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(Notifier.RINGING_ID, Notifier.build(this, top), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)

        if (wake == null) {
            wake = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tokei:ringing")
                .apply { acquire((settings.ringMinutes + 1) * 60_000L) }
        }
        if (playing?.same(top) != true) {
            stopSound()
            playing = top
            startSound(top, settings)
        }
        handler.removeCallbacks(stopAfter)
        val left = settings.ringSince + settings.ringMinutes * 60_000L - System.currentTimeMillis()
        handler.postDelayed(stopAfter, maxOf(1_000L, left))
        return START_NOT_STICKY
    }

    private fun startSound(r: Ring, settings: Settings) {
        val store = Store.get(this)
        val (sound, vibrate) = if (r.timer) {
            store.timer(r.id)?.let { it.sound to it.vibrate } ?: ("" to true)
        } else {
            store.alarm(r.id)?.let { it.sound to it.vibrate } ?: ("" to true)
        }
        val audio = getSystemService(AudioManager::class.java)
        val inCall = audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION

        if (sound != Alarm.SILENT) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs)
                .build()
                .also { audio.requestAudioFocus(it) }
            player = Sounds.candidates(this, sound).firstNotNullOfOrNull { uri -> open(uri, attrs) }
            if (player == null) Log.e("tokei", "no sound could be played for ${r.encode()}")
            val p = player
            if (p != null) {
                when {
                    inCall -> p.setVolume(0.1f, 0.1f)
                    settings.rise -> {
                        riseStart = System.currentTimeMillis()
                        p.setVolume(0.05f, 0.05f)
                        handler.post(rise)
                    }
                }
                p.start()
                Log.i("tokei", "playing for ${r.encode()}${if (inCall) " quietly, in a call" else ""}")
            }
        }
        if (vibrate || inCall) {
            vibrator = getSystemService(Vibrator::class.java).also {
                it.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 600, 600), 0),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                )
            }
        }
    }

    /** The volume, raised a step a second from quiet to full over [Settings.RISE_MS]. */
    private val rise = object : Runnable {
        override fun run() {
            val p = player ?: return
            val f = ((System.currentTimeMillis() - riseStart).toFloat() / Settings.RISE_MS).coerceIn(0.05f, 1f)
            runCatching { p.setVolume(f, f) }
            if (f < 1f) handler.postDelayed(this, 1_000L)
        }
    }

    private fun open(uri: Uri, attrs: AudioAttributes): MediaPlayer? = try {
        MediaPlayer().apply {
            setAudioAttributes(attrs)
            setDataSource(this@RingService, uri)
            isLooping = true
            setWakeMode(this@RingService, PowerManager.PARTIAL_WAKE_LOCK)
            prepare()
        }
    } catch (e: Exception) {
        Log.w("tokei", "cannot play $uri: ${e.message}")
        null
    }

    private fun stopSound() {
        handler.removeCallbacks(rise)
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        vibrator?.cancel()
        vibrator = null
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopSound()
        wake?.let { if (it.isHeld) it.release() }
        wake = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}

/** The sounds an alarm can have: the phone's own alarm sounds, and a file the person chose. */
object Sounds {

    /** What to try, in order: the chosen sound, the phone's alarm sound, then any alarm sound the phone has. */
    fun candidates(context: Context, sound: String): List<Uri> = buildList {
        if (sound.isNotBlank() && sound != Alarm.SILENT) add(Uri.parse(sound))
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)?.let { add(it) }
        addAll(list(context).take(3).map { it.second })
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)?.let { add(it) }
    }

    /** The phone's alarm sounds, by name. */
    fun list(context: Context): List<Pair<String, Uri>> = runCatching {
        val rm = RingtoneManager(context).apply { setType(RingtoneManager.TYPE_ALARM) }
        val c = rm.cursor
        buildList {
            while (c.moveToNext()) {
                add(c.getString(RingtoneManager.TITLE_COLUMN_INDEX) to rm.getRingtoneUri(c.position))
            }
        }
    }.getOrDefault(emptyList())
}

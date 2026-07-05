package ani.saikou.media.anime.mpv

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PlaybackService : Service() {

    private val TAG = "mpv"

    inner class LocalBinder : Binder() {
        val service get() = this@PlaybackService
    }
    private val binder = LocalBinder()

    lateinit var player: MpvVideoPlayer
        private set

    private lateinit var mediaSession: MediaSessionCompat
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)



    private val audioManager: AudioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private var focusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false


    private var resumeOnFocusGain = false

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {

                Log.d(TAG, "AUDIOFOCUS_LOSS ")
                hasAudioFocus = false
                resumeOnFocusGain = false
                player.pause()
                publishState(PlaybackStateCompat.STATE_PAUSED)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "AUDIOFOCUS_LOSS_TRANSIENT ")
                resumeOnFocusGain = player.isPlaying.value
                player.pause()
                publishState(PlaybackStateCompat.STATE_PAUSED)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "AUDIOFOCUS_GAIN")
                hasAudioFocus = true
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    player.play()
                    publishState(PlaybackStateCompat.STATE_PLAYING)
                }
            }
        }
    }
    fun setSystemVolume(level: Int) {

        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVolume = (level.coerceIn(0, 100) * maxVolume) / 100

        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            targetVolume,
            0
//            AudioManager.FLAG_SHOW_UI // disable system volume ui
        )
    }
    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true

        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build()

            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .setWillPauseWhenDucked(true)
                .build()

            focusRequest = request
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }

        hasAudioFocus = granted
        Log.d(TAG, "requestAudioFocus() -> granted=$granted")
        return granted
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus && focusRequest == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
        hasAudioFocus = false
        resumeOnFocusGain = false
        focusRequest = null
    }


    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                Log.d(TAG, "ACTION_AUDIO_BECOMING_NOISY ")
                player.pause()
                publishState(PlaybackStateCompat.STATE_PAUSED)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = MpvVideoPlayer(this)
        player.init(Unit)

        mediaSession = MediaSessionCompat(this, "SaikouSession").apply {
            setCallback(sessionCallback)
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
        }

        registerReceiver(becomingNoisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))

        serviceScope.launch {
            player.isPlaying.collect { playing ->
                if (playing && !hasAudioFocus) {
                    val granted = requestAudioFocus()
                    if (!granted) {
                        Log.w(TAG, "Audio focus request denied ")
                        player.pause()
                        publishState(PlaybackStateCompat.STATE_PAUSED)
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private val sessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() { player.play(); publishState(PlaybackStateCompat.STATE_PLAYING) }
        override fun onPause() { player.pause(); publishState(PlaybackStateCompat.STATE_PAUSED) }
        override fun onSeekTo(pos: Long) { player.seekTo(pos) }
        override fun onSkipToNext() { /* forward to a lambda you set from PlayerViewModel */ onNext?.invoke() }
        override fun onSkipToPrevious() { onPrev?.invoke() }
    }

    var onNext: (() -> Unit)? = null
    var onPrev: (() -> Unit)? = null

    fun setSessionActive(active: Boolean) { mediaSession.isActive = active }

    fun publishState(state: Int) {
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SEEK_TO or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(state, player.currentPosition.value, player.playbackSpeed.value)
                .build()
        )
    }

    fun updateMetadata(title: String, subtitle: String, durationMs: Long) {
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, subtitle)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
                .build()
        )
    }


    fun teardown() {
        abandonAudioFocus()
        mediaSession.isActive = false
        mediaSession.release()
        player.release()
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        try {
            unregisterReceiver(becomingNoisyReceiver)
        } catch (e: IllegalArgumentException) {

        }
        abandonAudioFocus()
        if (::mediaSession.isInitialized) mediaSession.release()
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }
}
package ani.saikou.media.anime.mpv

import `is`.xyz.mpv.MPVNode
import kotlinx.coroutines.flow.StateFlow



interface Player {

    // Initialization & Lifecycle
    fun init(surfaceHolder: Any)
    fun release()

    // Basic Playback Control
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun stop()

    // Media Loading
    fun loadMedia(
        videoUrl: String,
        headers: Map<String, String> = emptyMap(),
        startPositionMs: Long = 0L,
        audioTracks: List<ExternalAudio> = emptyList(),
        subtitles: List<ExternalSubtitle> = emptyList()
    )

    // Playback State
    val isPlaying: StateFlow<Boolean>
    val currentPosition: StateFlow<Long>
    val duration: StateFlow<Long>
    val playbackState: StateFlow<PlaybackState>

    enum class PlaybackState {
        IDLE, BUFFERING, READY, ENDED
    }

    // Audio
    val audioTracks: StateFlow<List<AudioTrack>>
    fun selectAudioTrack(trackId: Int)
    fun setExternalAudioTrack(
        url: String,
        headers: Map<String, String> = emptyMap()
    )

    val audioChannel: StateFlow<AudioChannel>
    fun setAudioChannel(channel: AudioChannel)

    enum class AudioChannel(val value: String) {
        AUTO("auto"),
        STEREO("stereo"),
        MONO("mono"),
        LEFT("left"),
        RIGHT("right")
    }

    // Subtitles
    val subtitleTracks: StateFlow<List<SubtitleTrack>>
    fun selectSubtitleTrack(trackId: Int)
    fun setExternalSubtitles(subtitles: List<ExternalSubtitle>)

    // Video
    val videoTracks: StateFlow<List<VideoTrack>>
    fun selectVideoTrack(trackId: Int)

    // Volume
    val volume: StateFlow<Int>
    fun setVolume(level: Int)
    fun increaseVolume(step: Int = 5)
    fun decreaseVolume(step: Int = 5)

    // Video Scaling / Aspect Ratio
    val videoScaleMode: StateFlow<VideoScaleMode>
    fun setVideoScaleMode(mode: VideoScaleMode)

    enum class VideoScaleMode {
        FIT,        // Letterbox / Pillarbox
        STRETCH,    // Fill (distort)
        CROP,       // Zoom to fill
        ZOOM,       // Slight zoom
        ORIGINAL    // No scaling
    }

    // Decoder
    val currentDecoder: StateFlow<Decoder>
    val availableDecoders: StateFlow<List<Decoder>>
    fun setDecoder(decoder: Decoder)

    enum class Decoder(val title: String, val value: String) {
        AUTO("Auto", "auto"),
        AUTO_COPY("Auto Copy", "auto-copy"),
        SOFTWARE("Software", "no"),
        HARDWARE("Hardware", "mediacodec-copy"),
        HARDWARE_PLUS("Hardware+", "mediacodec");

        companion object {
            fun fromValue(value: String): Decoder? =
                entries.firstOrNull { it.value == value }
        }
    }

    // Advanced
    fun command(vararg args: String)
    fun setPlaybackSpeed(speed: Float)
    val playbackSpeed: StateFlow<Float>
}

/** Data Classes */

data class AudioTrack(
    val id: Int,
    val name: String,
    val language: String?,
    val codec: String? = null,
    val channels: Int? = null,
    val isDefault: Boolean = false,
    val isSelected: Boolean = false
)

data class SubtitleTrack(
    val id: Int,
    val name: String,
    val language: String?,
    val codec: String? = null,
    val isDefault: Boolean = false,
    val isSelected: Boolean = false,
    val isForced: Boolean = false,
    val isExternal: Boolean = false
)

data class VideoTrack(
    val id: Int,
    val name: String,
    val codec: String? = null,
    val resolution: String? = null,
    val isSelected: Boolean = false
)

data class ExternalSubtitle(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val language: String? = null,
    val label: String? = null
)


data class ExternalAudio(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val label: String? = null,
    val language: String? = null
)


object TrackParser {

    fun parseTrackList(node: MPVNode?): Triple<List<AudioTrack>, List<SubtitleTrack>, List<VideoTrack>> {
        val audio = mutableListOf<AudioTrack>()
        val subtitle = mutableListOf<SubtitleTrack>()
        val video = mutableListOf<VideoTrack>()

        val array = node?.asArray() ?: return Triple(emptyList(), emptyList(), emptyList())

        for (trackNode in array) {
            val map = trackNode.asMap() ?: continue

            val type = map["type"]?.asString() ?: continue
            val id = map["id"]?.asInt()?.toInt() ?: continue
            val title = map["title"]?.asString() ?: "Track $id"
            val lang = map["lang"]?.asString()
            val codec = map["codec"]?.asString()
            val isDefault = map["default"]?.asBoolean() ?: false
            val isSelected = map["selected"]?.asBoolean() ?: false
            val isForced = map["forced"]?.asBoolean() ?: false
            val isExternal = map["external"]?.asBoolean() ?: false

            when (type) {
                "audio" -> {
                    val channels = map["channels"]?.asInt()?.toInt()
                    audio.add(
                        AudioTrack(
                            id = id,
                            name = title,
                            language = lang,
                            codec = codec,
                            channels = channels,
                            isDefault = isDefault,
                            isSelected = isSelected
                        )
                    )
                }

                "sub" -> {
                    subtitle.add(
                        SubtitleTrack(
                            id = id,
                            name = title,
                            language = lang,
                            codec = codec,
                            isDefault = isDefault,
                            isSelected = isSelected,
                            isForced = isForced,
                            isExternal = isExternal
                        )
                    )
                }

                "video" -> {
                    val width = map["width"]?.asInt()?.toInt() ?: 0
                    val height = map["height"]?.asInt()?.toInt() ?: 0
                    val resolution = if (width > 0 && height > 0) "${width}x$height" else null

                    video.add(
                        VideoTrack(
                            id = id,
                            name = title,
                            codec = codec,
                            resolution = resolution,
                            isSelected = isSelected
                        )
                    )
                }
            }
        }

        return Triple(audio, subtitle, video)
    }
}





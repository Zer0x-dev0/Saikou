package ani.saikou.parsers.anime.extractors

import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.parsers.Subtitle
import ani.saikou.parsers.Video
import ani.saikou.parsers.VideoContainer
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.VideoType
import ani.saikou.tryWithSuspend
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(InternalSerializationApi::class)
class AniZoneExtractor(override val server: VideoServer) : VideoExtractor() {


    @Serializable
    data class SourceItem(
        val url: String,
        val isM3u8: Boolean,
        val type: String
    )

    @Serializable

    data class Subtitles(
        val url: String,
        val lang: String,
//        val kind:String, fix the api stuff
        val default: Boolean
    )

    @Serializable
    data class SourceData(
        val subtitles: List<Subtitles> = emptyList(),
        val sources: List<SourceItem> = emptyList()
    )

    @Serializable
    data class SourceResponse(
        val headers: Headers,
        val data: SourceData
    )

    @Serializable
    data class Headers(
        @SerialName("Referer")
        val referer: String? = null
    )

    override suspend fun extract(): VideoContainer {
        return tryWithSuspend(post = false, snackbar = true) {
            val response = client.get(server.embed.url,headers = mapOf("x-api-key" to apiKey))
                .parsed<SourceResponse>()


            val videos = response.data.sources.map {
                val videoType = if (it.isM3u8) VideoType.M3U8 else VideoType.CONTAINER
                Video(
                    quality = null,
                    format = videoType,
                    file = FileUrl(it.url),
                    extraNote = it.type
                )
            }

            val realSubtitles = response.data.subtitles.filter { it.lang != "thumbnails" }

            val subs = realSubtitles.map { sub ->
                Subtitle(
                    language = sub.lang,
                    url = sub.url
                )
            }
            VideoContainer(videos,subs)

        } ?: VideoContainer(emptyList())

    }
}

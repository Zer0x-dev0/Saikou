package ani.saikou.parsers.anime.extractors

import android.util.Log
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
import org.jsoup.nodes.Document
import java.net.URI

@OptIn(InternalSerializationApi::class)
class AniDBExtractor(
    override val server: VideoServer
) : VideoExtractor() {


    data class SourceItem(
        val url: String,
        val isM3u8: Boolean,
        val type: String
    )

    override suspend fun extract(): VideoContainer {
        return tryWithSuspend(post = false, snackbar = true) {

            val response = client
                .get(server.embed.url)

            Log.d("anidb", "Response${response.code}")
            val html = response.document
            val sources = parseSources(html)

            val referer = URI(server.embed.url)
                .resolve("/")
                .toString()

            val videos = sources.map {
                Video(
                    quality = null,
                    format = if (it.isM3u8) VideoType.M3U8 else VideoType.CONTAINER,
                    file = FileUrl(
                        url = it.url,
                        headers = mapOf(
                            "Referer" to referer
                        )
                    ),
                    extraNote = server.name
                )
            }

            VideoContainer(
                videos = videos,
                subtitles = emptyList()
            )

        } ?: VideoContainer(emptyList())
    }

    private fun parseSources(document: Document): List<SourceItem> {

        val regex = Regex(
            """file:\s*'([^']+)'\s*,\s*type:\s*'([^']+)'"""
        )

        document.select("script").forEach { script ->

            val content = script.data()

            if (!content.contains("sources:")) return@forEach

            return regex.findAll(content).map {
                SourceItem(
                    url = it.groupValues[1],
                    isM3u8 = it.groupValues[2] == "hls",
                    type = it.groupValues[2]
                )
            }.toList()
        }

        return emptyList()
    }
}
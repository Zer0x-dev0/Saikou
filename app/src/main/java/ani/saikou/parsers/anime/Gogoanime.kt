package ani.saikou.parsers.anime

import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.parsers.AnimeApiParser
import ani.saikou.parsers.Episode
import ani.saikou.parsers.ShowResponse
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.anime.extractors.MegaPlay
import ani.saikou.tryWithSuspend
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import java.net.URLEncoder

@OptIn(InternalSerializationApi::class)
class Gogoanime : AnimeApiParser() {

    override val name = "Gogoanime"
    override val saveName = "gogoanime"
    override val providerName = "gogo"
    override val malSyncBackupName = "Gogoanime"
    override val isDubAvailableSeparately = false

    override suspend fun search(query: String): List<ShowResponse> {
        return tryWithSuspend(post = true, snackbar = true) {
            if (query.isBlank()) return@tryWithSuspend emptyList()

            val encoded = URLEncoder.encode(query, "utf-8")
            val res = client.get(
                "$hostUrl/api/gogo/anime/search?q=$encoded",
                headers = mapOf("x-api-key" to apiKey)
            ).parsed<SearchApiResponse>()

            res.data.map {
                val title = it.name ?: it.title ?: it.romaji ?: "Unknown"
                ShowResponse(
                    name = title,
                    link = it.id,
                    coverUrl = FileUrl(it.posterImage ?: "")
                )
            }
        } ?: emptyList()
    }

    override suspend fun loadEpisodes(
        animeLink: String,
        extra: Map<String, String>?
    ): List<Episode> {
        return tryWithSuspend(post = true, snackbar = true) {
            if (animeLink.isBlank()) return@tryWithSuspend emptyList()

            val url = "$hostUrl/api/gogo/anime/$animeLink/episodes"
            val res = client.get(url, headers = mapOf("x-api-key" to apiKey)).parsed<EpisodesResponse>()

            res.data.map { ep ->
                Episode(
                    number = ep.episodeNumber?.toString() ?: "0",
                    link = ep.episodeId,
                    title = ep.title ?: "Episode ${ep.episodeNumber}",
                )
            }
        } ?: emptyList()
    }

    override suspend fun loadVideoServers(
        episodeLink: String,
        extra: Map<String, String>?
    ): List<VideoServer> {
        return tryWithSuspend(post = false, snackbar = true) {
            if (episodeLink.isBlank()) return@tryWithSuspend emptyList()
            val res = client.get(
                "$hostUrl/api/gogo/episode/$episodeLink/servers",
                headers = mapOf("x-api-key" to apiKey)
            ).parsed<EpisodeServersResponse>()

            val allServers = mutableListOf<VideoServer>()

            fun addServers(version: String, list: List<ServerItem>) {
                list.forEach { item ->
                    val serverName = "${version.uppercase()} - ${item.serverName}"
                    val embedUrl = "$hostUrl/api/gogo/sources/$episodeLink?version=$version&server=${item.serverName}"
                    allServers += VideoServer(
                        name = serverName,
                        embed = FileUrl(embedUrl)
                    )
                }
            }

            addServers("sub", res.data?.sub ?: emptyList())
            addServers("dub", res.data?.dub ?: emptyList())

            allServers
        } ?: emptyList()
    }

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor {
        return MegaPlay(server)
    }

    @Serializable
    private data class SearchApiResponse(
        val data: List<SearchItems> = emptyList()
    )

    @Serializable
    private data class SearchItems(
        val id: String,
        val name: String? = null,
        val title: String? = null,
        val romaji: String? = null,
        val posterImage: String? = null
    )

    @Serializable
    private data class EpisodesResponse(
        val data: List<EpisodeItem> = emptyList()
    )

    @Serializable
    private data class EpisodeItem(
        val episodeId: String,
        val title: String? = null,
        val episodeNumber: Int? = null,
    )

    @Serializable
    private data class EpisodeServersResponse(
        val data: EpisodeServers? = null
    )

    @Serializable
    private data class ServerItem(
        val serverName: String,
        val serverId: String
    )

    @Serializable
    private data class EpisodeServers(
        val sub: List<ServerItem> = emptyList(),
        val dub: List<ServerItem> = emptyList(),
        val raw: List<ServerItem> = emptyList(),
        val episodeNumber: Int? = null
    )
}

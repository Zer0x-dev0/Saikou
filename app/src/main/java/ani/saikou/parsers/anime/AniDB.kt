package ani.saikou.parsers.anime

import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.parsers.AnimeApiParser
import ani.saikou.parsers.AnimeParser
import ani.saikou.parsers.Episode
import ani.saikou.parsers.ShowResponse
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.anime.extractors.AniDBExtractor


import ani.saikou.tryWithSuspend
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import java.net.URLEncoder

@OptIn(InternalSerializationApi::class)
class AniDB : AnimeApiParser() {

    override val name = "AniDB"
    override val saveName = "AniDB"
    override val isDubAvailableSeparately = false

    override val providerName = "anidb"

    override suspend fun search(query: String): List<ShowResponse> {
        return tryWithSuspend(post = false, snackbar = true) {
            if (query.isBlank()) return@tryWithSuspend emptyList()
            val encoded = URLEncoder.encode(query, "utf-8")
            val res = client.get(
                "$hostUrl/api/anidb/anime/search?q=$encoded",
                headers = mapOf("x-api-key" to apiKey)
            )
                .parsed<SearchApiResponse>()

            res.data.map {
                ShowResponse(
                    name = it.name,
                    link = it.id,
                    coverUrl = FileUrl(it.posterImage)
                )
            }
        } ?: emptyList()

    }

    override suspend fun loadEpisodes(
        animeLink: String,
        extra: Map<String, String>?
    ): List<Episode> {
        return tryWithSuspend(post = false, snackbar = true) {
            if (animeLink.isBlank()) return@tryWithSuspend emptyList()
            val url = "$hostUrl/api/anidb/anime/$animeLink/episodes"
            val res =
                client.get(url, headers = mapOf("x-api-key" to apiKey)).parsed<EpisodesResponse>()

            res.data.map { ep ->
                Episode(
                    number = ep.episodeNumber.toString(),
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
            val res = client.get(
                "$hostUrl/api/anidb/episode/$episodeLink/servers",
                headers = mapOf("x-api-key" to apiKey)
            ).parsed<EpisodeServersResponse>()

            val allServers = mutableListOf<VideoServer>()

            res.data.sub.forEach { server ->
                allServers += VideoServer(
                    name = "Sub - ${server.serverName}",
                    embed = FileUrl(server.serverId)
                )
            }

            res.data.dub.forEach { server ->
                allServers += VideoServer(
                    name = "Dub - ${server.serverName}",
                    embed = FileUrl(server.serverId)
                )
            }


            allServers
        } ?: emptyList()
    }

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor {
        return AniDBExtractor(server)
    }


    @Serializable
    private data class SearchApiResponse(
        val data: List<SearchItems>
    )


    @Serializable
    private data class SearchItems(
        val id: String,
        val name: String,
        val posterImage: String

    )


    @Serializable
    private data class EpisodesResponse(
        val data: List<EpisodeItem>
    )

    @Serializable
    private data class EpisodeItem(
        val episodeId: String,
        val title: String?,
        val episodeNumber: Int,

        )

    @Serializable
    private data class EpisodeServersResponse(
        val data: EpisodeServers
    )

    @Serializable
    private data class ServerItem(
        val serverName: String,
        val serverId: String,

        )

    @Serializable
    private data class EpisodeServers(
        val sub: List<ServerItem> = emptyList(),
        val dub: List<ServerItem> = emptyList(),
        val raw: List<ServerItem> = emptyList(),

        )


}
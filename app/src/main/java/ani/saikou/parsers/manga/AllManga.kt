package ani.saikou.parsers.manga

import android.util.Log
import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.parsers.MangaChapter
import ani.saikou.parsers.MangaImage
import ani.saikou.parsers.MangaParser
import ani.saikou.parsers.ShowResponse
import ani.saikou.tryWithSuspend
import com.lagradost.nicehttp.JsonAsString
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(InternalSerializationApi::class)
class AllManga : MangaParser() {

    override val name = "AllManga"
    override val saveName = "AllManga"

    override val hostUrl: String = "https://api.allanime.day/api"
    private val posterBase = "https://wp.youtube-anime.com/aln.youtube-anime.com/"
    private val imageReferer = "https://mkissa.to/"

    private val mapper = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val headers = mapOf(
        "Accept" to "application/json",
        "Accept-Language" to "en-US,en;q=0.9",
        "Content-Type" to "application/json",
        "Origin" to "https://mkissa.to",
        "Referer" to "https://mkissa.to/",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    )

    private fun buildQuery(queryAction: () -> String): String =
        queryAction().trimIndent().replace("%", "$")

    private val searchQuery = buildQuery {
        """
        query (%search: SearchInput, %limit: Int, %page: Int, %countryOrigin: VaildCountryOriginEnumType) {
            mangas(search: %search, limit: %limit, page: %page, countryOrigin: %countryOrigin) {
                edges { _id name englishName nativeName thumbnail }
            }
        }
        """
    }

    private val detailsQuery = buildQuery {
        """
        query (%id: String!) {
            manga(_id: %id) { availableChaptersDetail }
        }
        """
    }

    private val pageQuery = buildQuery {
        """
        query (%mangaId: String!, %chapterString: String!, %translationType: VaildTranslationTypeMangaEnumType!) {
            chaptersForRead(mangaId: %mangaId, chapterString: %chapterString, translationType: %translationType) {
                edges {
                    pictureUrlHead
                    pictureUrls
                }
            }
        }
        """
    }

    override suspend fun search(query: String): List<ShowResponse> = tryWithSuspend {
        if (query.isBlank()) return@tryWithSuspend emptyList<ShowResponse>()

        val variables = SearchVariables(
            search = SearchInput(query, allowAdult = false, allowUnknown = false),
            limit = 30,
            page = 1,
            countryOrigin = "ALL"
        )

        val jsonBody = mapper.encodeToString(SearchGraphQLRequest(searchQuery, variables))
        val response = client.post(hostUrl, headers = headers, json = JsonAsString(jsonBody))
            .parsed<SearchResponse>()

        response.data?.mangas?.edges?.map {
            val title = it.englishName ?: it.name ?: it.nativeName ?: "Unknown"
            val compositeId = "${createSlug(it.name ?: it.englishName)}-${it._id}"
            ShowResponse(
                name = title, link = compositeId, coverUrl = FileUrl(
                    posterBase + (it.thumbnail ?: ""), mapOf("Referer" to imageReferer)
                )
            )
        } ?: emptyList()
    } ?: emptyList()

    override suspend fun loadChapters(
        mangaLink: String, extra: Map<String, String>?
    ): List<MangaChapter> = tryWithSuspend {
        val mediaId = mangaLink.substringAfterLast("-")
        Log.d("MANGA_API", "Fetching chapters for mediaId: $mediaId")

        val jsonBody =
            mapper.encodeToString(DetailsGraphQLRequest(detailsQuery, IdVariable(mediaId)))
        val responseRaw = client.post(hostUrl, headers = headers, json = JsonAsString(jsonBody))
        
        val response = responseRaw.parsed<DetailsResponse>()

        val detail = response.data?.manga?.availableChaptersDetail
        val chapters = (detail?.sub ?: emptyList()) + (detail?.raw ?: emptyList())

        Log.d("MANGA_API", "Found ${chapters.size} chapters")

        chapters.distinct().map { rawChapterStr ->
            MangaChapter(
                number = rawChapterStr, link = "${mediaId}-chapter-$rawChapterStr"
            )
        }.sortedBy { it.number.toFloatOrNull() ?: 0f }
    } ?: emptyList()

    override suspend fun loadImages(chapterLink: String): List<MangaImage> = tryWithSuspend {
        val match = Regex("(.+)-chapter-(.+)", RegexOption.IGNORE_CASE).find(chapterLink)
            ?: return@tryWithSuspend emptyList<MangaImage>()

        val mangaId = match.groupValues[1]
        val chapterNumStr = match.groupValues[2]

        Log.d("MANGA_API", "📥 Loading Images for: $mangaId, Chapter: $chapterNumStr")

        val variables = PageVariables(
            mangaId = mangaId,
            chapterString = chapterNumStr,
            translationType = "sub"
        )

        val jsonRequest = PageGraphQLRequest(pageQuery, variables)
        val jsonBody = mapper.encodeToString(jsonRequest)

        val responseRaw = client.post(hostUrl, headers = headers, json = JsonAsString(jsonBody))
        
        val response = responseRaw.parsed<PageResponse>()

        val edges = response.data?.chaptersForRead?.edges ?: emptyList()
        if (edges.isEmpty()) {
            Log.e("MANGA_API", "No edges found in chaptersForRead")
            return@tryWithSuspend emptyList<MangaImage>()
        }

        val images = edges.firstOrNull { it.pictureUrls.isNotEmpty() }?.let { itEdge ->
            var head = itEdge.pictureUrlHead ?: "aln.youtube-anime.com"
            if (!head.startsWith("http")) head = "https://$head"
            if (!head.endsWith("/")) head += "/"

            itEdge.pictureUrls.map { pic ->
                val url = if (pic.url.startsWith("/")) pic.url.substring(1) else pic.url
                MangaImage(
                    FileUrl(
                        head + url,
                        mapOf("Referer" to imageReferer)
                    )
                )
            }
        } ?: emptyList()

        Log.d("MANGA_API", "Found ${images.size} images. First URL: ${images.firstOrNull()?.url?.url}")
        images
    } ?: emptyList()

    @Serializable
    data class SearchGraphQLRequest(
        @SerialName("query") val query: String,
        @SerialName("variables") val variables: SearchVariables
    )

    @Serializable
    data class DetailsGraphQLRequest(
        @SerialName("query") val query: String, @SerialName("variables") val variables: IdVariable
    )

    @Serializable
    data class PageGraphQLRequest(
        @SerialName("query") val query: String,
        @SerialName("variables") val variables: PageVariables
    )

    @Serializable
    data class SearchVariables(
        @SerialName("search") val search: SearchInput,
        @SerialName("limit") val limit: Int,
        @SerialName("page") val page: Int,
        @SerialName("countryOrigin") val countryOrigin: String
    )

    @Serializable
    data class SearchInput(
        @SerialName("query") val query: String,
        @SerialName("allowAdult") val allowAdult: Boolean,
        @SerialName("allowUnknown") val allowUnknown: Boolean
    )

    @Serializable
    data class IdVariable(@SerialName("id") val id: String)

    @Serializable
    data class PageVariables(
        @SerialName("mangaId") val mangaId: String,
        @SerialName("chapterString") val chapterString: String,
        @SerialName("translationType") val translationType: String
    )

    @Serializable
    data class SearchResponse(val data: SearchData? = null)

    @Serializable
    data class DetailsResponse(val data: DetailsData? = null)

    @Serializable
    data class PageResponse(val data: PageData? = null)

    @Serializable
    data class PageData(
        @SerialName("chaptersForRead") val chaptersForRead: ChapterPages? = null
    )

    @Serializable
    data class SearchData(val mangas: MangaConnection? = null)

    @Serializable
    data class MangaConnection(val edges: List<SearchManga> = emptyList())

    @Serializable
    class SearchManga(
        val _id: String,
        val name: String? = null,
        val englishName: String? = null,
        val nativeName: String? = null,
        val thumbnail: String? = null
    )

    @Serializable
    data class DetailsData(val manga: MangaDetails? = null)

    @Serializable
    data class MangaDetails(val availableChaptersDetail: AvailableChaptersDetail? = null)

    @Serializable
    data class AvailableChaptersDetail(
        val sub: List<String> = emptyList(),
        val raw: List<String> = emptyList()
    )

    @Serializable
    data class ChapterPages(val edges: List<Edge> = emptyList())

    @Serializable
    data class Edge(
        val pictureUrlHead: String? = null,
        val pictureUrls: List<PictureUrl>
    )
    @Serializable
    data class PictureUrl(
        val url: String,
        val num: Int? = null
    )

    private fun createSlug(text: String?): String =
        text?.lowercase()?.replace("[^a-z0-9]+".toRegex(), "-")?.trim('-') ?: "unknown"
}

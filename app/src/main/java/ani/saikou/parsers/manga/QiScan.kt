package ani.saikou.parsers.manga

import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.parsers.MangaChapter
import ani.saikou.parsers.MangaImage
import ani.saikou.parsers.MangaParser
import ani.saikou.parsers.ShowResponse
import ani.saikou.tryWithSuspend
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(InternalSerializationApi::class)
class QiScan : MangaParser() {
    override val name = "QiScan"
    override val saveName = "qiscan"
    override val hostUrl = "https://qimanga.com"
    private val apiUrl = "https://api.qimanga.com/api/v1"

    override suspend fun search(query: String): List<ShowResponse> = tryWithSuspend {
        val url = "$apiUrl/series/search?q=${encode(query)}"
        val response = client.get(url).parsed<SearchResponse>()
        response.data.map {
            ShowResponse(
                name = it.title,
                link = it.slug,
                coverUrl = it.cover
            )
        }
    } ?: emptyList()

    override suspend fun loadChapters(mangaLink: String, extra: Map<String, String>?): List<MangaChapter> = tryWithSuspend {
        val chapters = mutableListOf<MangaChapter>()
        var page = 1
        var totalPages: Int
        do {
            val url = "$apiUrl/series/$mangaLink/chapters?page=$page"
            val response = client.get(url).parsed<ChaptersResponse>()
            response.data.filter { it.isFree == true }.forEach {
                chapters.add(
                    MangaChapter(
                        number = it.number.toString().removeSuffix(".0"),
                        link = "$mangaLink/${it.slug}"
                    )
                )
            }
            totalPages = response.totalPages ?: 0
            page++
        } while (page <= totalPages)
        chapters.reversed()
    } ?: emptyList()

    override suspend fun loadImages(chapterLink: String): List<MangaImage> = tryWithSuspend {
        val parts = chapterLink.split("/")
        if (parts.size < 2) return@tryWithSuspend emptyList<MangaImage>()
        
        val seriesSlug = parts[0]
        val chapterSlug = parts[1]
        
        val url = "$apiUrl/series/$seriesSlug/chapters/$chapterSlug"
        val response = client.get(url).parsed<ChapterDetailResponse>()
        response.images.map {
            MangaImage(FileUrl(it.url, mapOf("Referer" to hostUrl)))
        }
    } ?: emptyList()

    @Serializable
    data class SearchResponse(val data: List<MangaData>)

    @Serializable
    data class MangaData(val slug: String, val title: String, val cover: String)

    @Serializable
    data class ChaptersResponse(val data: List<ChapterData>, val totalPages: Int? = null)

    @Serializable
    data class ChapterData(val slug: String, val number: Float, val isFree: Boolean? = null)

    @Serializable
    data class ChapterDetailResponse(val images: List<ImageData>)

    @Serializable
    data class ImageData(val url: String)
}

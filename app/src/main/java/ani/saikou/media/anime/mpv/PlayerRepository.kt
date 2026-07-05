package ani.saikou.media.anime.mpv

import android.annotation.SuppressLint
import android.util.Log
import ani.saikou.client
import ani.saikou.tryWithSuspend
import kotlinx.serialization.Serializable

class PlayerRepository {

    private val tag = "mpv"

    /**
     * Fetches opening, ending, recap and mixed skip segments from the alternative
     * timestamp provider (TheIntroDB.org) using an AniList ID.
     *
     * @param anilistId AniList media ID.
     * @param episodeNumber Episode number to fetch timestamps for.
     * @param durationMs Episode duration in milliseconds.
     * @return A unified list of skip intervals, or `null` if no timestamps are available
     * or the request fails.
     */
    suspend fun fetchSkipTimes(anilistId: Int, episodeNumber: Int, durationMs: Long): List<SkipInterval>? {
        val url = "https://api.kenjitsu.workers.dev/api/meta/skip-times/$anilistId?episodeNumber=$episodeNumber&durationMs=$durationMs"

        return tryWithSuspend {
            val response = client.get(url)
            val data = response.parsed<SkipTimeResponse>()

            mapWorkersToUnified(data)
        }
    }

    /**
     * Fetches opening, ending, recap and mixed skip segments from AniSkip using a
     * MyAnimeList ID.
     *
     * @param malId MyAnimeList media ID.
     * @param episodeNumber Episode number to fetch timestamps for.
     * @param episodeLength Episode duration in **seconds**.
     * @return A unified list of skip intervals, or `null` if no timestamps are available
     * or the request fails.
     */
    suspend fun fetchAniSkipTimes(malId: Int, episodeNumber: Int, episodeLength: Long): List<SkipInterval>? {
        val url =
            "https://api.aniskip.com/v2/skip-times/$malId/$episodeNumber?types[]=ed&types[]=mixed-ed&types[]=mixed-op&types[]=op&types[]=recap&episodeLength=$episodeLength"

        return tryWithSuspend {
            val response = client.get(url)
            Log.d(tag, "AniSkip response code: ${response.code}")

            val res = response.parsed<AniSkipResponse>()
            Log.d(tag, "AniSkip response data: $res")

            if (res.found) res.results?.let { mapAniSkipToUnified(it) } else null
        }
    }

    private fun mapWorkersToUnified(res: SkipTimeResponse): List<SkipInterval> {
        val list = mutableListOf<SkipInterval>()

        res.intro.forEach { list.add(SkipInterval(it.startMs, it.endMs ?: 0, "Opening")) }
        res.recap.forEach { list.add(SkipInterval(it.startMs, it.endMs ?: 0, "Recap")) }
        res.credits.forEach { list.add(SkipInterval(it.startMs, it.endMs ?: 0, "Ending")) }
        res.preview.forEach { list.add(SkipInterval(it.startMs, it.endMs ?: 0, "Preview")) }

        return list
    }

    private fun mapAniSkipToUnified(stamps: List<AniSkipStamp>): List<SkipInterval> {
        return stamps.map { stamp ->
            SkipInterval(
                startTimeMs = (stamp.interval.startTime * 1000).toLong(),
                endTimeMs = (stamp.interval.endTime * 1000).toLong(),
                type = stamp.skipType.toDisplayType()
            )
        }
    }

    private fun String.toDisplayType(): String = when (this) {
        "op" -> "Opening"
        "ed" -> "Ending"
        "recap" -> "Recap"
        "mixed-ed" -> "Mixed Ending"
        "mixed-op" -> "Mixed Opening"
        else -> this
    }


    @SuppressLint("UnsafeOptInUsageError")
    @Serializable
    data class SkipTimeResponse(
        val tmdbId: Int,
        val type: String,
        val season: Int,
        val episode: Int,
        val intro: List<SkipSegment>,
        val recap: List<SkipSegment>,
        val credits: List<SkipSegment>,
        val preview: List<SkipSegment>
    )

    @Serializable
    @SuppressLint("UnsafeOptInUsageError")
    data class SkipSegment(
        val startMs: Long,
        val endMs: Long?,
        val durationMs: Long?,
        val startsAtBeginning: Boolean,
        val endsAtMediaEnd: Boolean
    )


    @SuppressLint("UnsafeOptInUsageError")
    @Serializable
    data class AniSkipResponse(
        val found: Boolean,
        val results: List<AniSkipStamp>?,
        val message: String?,
        val statusCode: Int
    )

    @SuppressLint("UnsafeOptInUsageError")
    @Serializable
    data class AniSkipStamp(
        val interval: AniSkipInterval,
        val skipType: String,
        val skipId: String,
        val episodeLength: Double
    )

    @SuppressLint("UnsafeOptInUsageError")
    @Serializable
    data class AniSkipInterval(
        val startTime: Double,
        val endTime: Double
    )


    data class SkipInterval(
        val startTimeMs: Long,
        val endTimeMs: Long,
        val type: String
    )
}
package ani.saikou.others

import android.annotation.SuppressLint
import android.util.Log
import ani.saikou.client
import ani.saikou.tryWithSuspend
import kotlinx.serialization.Serializable
import java.net.URLEncoder

object AniSkip {


    suspend fun getResult(malId: Int, episodeNumber: Int, episodeLength: Long): List<Stamp>? {
        val url =
            "https://api.aniskip.com/v2/skip-times/$malId/$episodeNumber?types[]=ed&types[]=mixed-ed&types[]=mixed-op&types[]=op&types[]=recap&episodeLength=$episodeLength"
        val TAG = "mpv"
        return tryWithSuspend {
            val response = client.get(url)


            Log.d(TAG, "Response Code: ${response.code}")

            val res = response.parsed<AniSkipResponse>()


            Log.d(TAG, "Response Data: $res")

            if (res.found) res.results else null
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    @Serializable
    data class AniSkipResponse(
        val found: Boolean,
        val results: List<Stamp>?,
        val message: String?,
        val statusCode: Int
    )

    @SuppressLint("UnsafeOptInUsageError")
    @Serializable
    data class Stamp(
        val interval: AniSkipInterval,
        val skipType: String,
        val skipId: String,
        val episodeLength: Double
    )


    fun String.getType(): String {
        return when (this) {
            "op" -> "Opening"
            "ed" -> "Ending"
            "recap" -> "Recap"
            "mixed-ed" -> "Mixed Ending"
            "mixed-op" -> "Mixed Opening"
            else -> this
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    @Serializable
    data class AniSkipInterval(
        val startTime: Double,
        val endTime: Double
    )
}
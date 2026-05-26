package ani.saikou.media.anime.mpv



import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ani.saikou.media.Media
import ani.saikou.*
import ani.saikou.media.anime.Episode /// which episodeImport
import ani.saikou.others.AniSkip
import ani.saikou.parsers.Video
import ani.saikou.parsers.VideoExtractor


import ani.saikou.saveData
/// i will implement aniskip logic here , previous and next episode changes , saving progress media etc idk pass the data to playerviewmodel???

object PlaybackSession {

    fun savePlaybackPosition(){}
    fun clearSession(){}
    fun loadAniSkipData(){}


    fun nextEpisode(){}

    fun currentEpisode(){

    }
    fun previousEpisode(){}

    fun initDiscordRpc(){}


}
package ani.saikou.parsers

import ani.saikou.Lazier
import ani.saikou.lazyList
import ani.saikou.parsers.anime.AllAnime
import ani.saikou.parsers.anime.Anikoto

import ani.saikou.parsers.anime.AnimePahe
import ani.saikou.parsers.anime.Gogoanime
//import ani.saikou.parsers.anime.Aniwatchtv
import ani.saikou.parsers.anime.Haho
import ani.saikou.parsers.anime.HentaiFF
import ani.saikou.parsers.anime.HentaiMama
import ani.saikou.parsers.anime.HentaiStream
import ani.saikou.parsers.anime.Anizone
import ani.saikou.parsers.anime.AniDB
import ani.saikou.parsers.anime.AnimeHeaven
import ani.saikou.parsers.anime.AniBD


object AnimeSources : WatchSources() {
    override val list: List<Lazier<BaseParser>> = lazyList(

//        "AllAnime" to ::AllAnime,
//        "AniDB" to ::AniDB,
//        "Gogoanime" to ::Gogoanime,
        "Anikoto" to ::Anikoto,
        "AniBD" to ::AniBD,
//        "AnimePahe" to ::AnimePahe,
        "Anizone" to ::Anizone,
//        "AnimeHeaven" to ::AnimeHeaven
        )
}

object HAnimeSources : WatchSources() {
    private val aList: List<Lazier<BaseParser>> = lazyList(
        "HentaiMama" to ::HentaiMama,
        "Haho" to ::Haho,
        "HentaiStream" to ::HentaiStream,
        "HentaiFF" to ::HentaiFF,
    )

    override val list = listOf(aList, AnimeSources.list).flatten()
}

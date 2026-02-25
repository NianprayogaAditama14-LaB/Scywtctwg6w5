package com.bstation

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson

class BstationProvider : MainAPI() {
    override var mainUrl = "https://www.bilibili.tv"
    override var name = "Bstation🥙"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    private val apiUrl = "https://api.bilibili.tv"
    private val biliintlApiUrl = "https://api.biliintl.com"
    private val subtitleProxyUrl = "https://bstation-subtitle.cf1-e6a.workers.dev"
    private val cookieString = "SESSDATA=d3e2b1e9,1785599046,be897*210091; bili_jct=c354fd55e047c9b7daddc250b5004972; DedeUserID=1709563281; DedeUserID__ckMd5=4568e91a427e5c0dd0403fdd96efae6f; mid=1709563281; buvid3=f165a4d3-71ca-42fb-aa5a-956c0eae673a44290infoc; buvid4=193EE759-E26D-6A17-9E3A-F6515909D4AF56972-026020223-VQcoVjaTucTuIONkEeQ0RA==; joy_jct=c354fd55e047c9b7daddc250b5004972; bstar-web-lang=id; bsource=search_google"
    private val cookies = mapOf<String, String>()
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36",
        "Referer" to "https://www.bilibili.tv/",
        "Origin" to "https://www.bilibili.tv",
        "Cookie" to cookieString
    )

    override val mainPage = mainPageOf(
        "timeline" to "Rilis Terbaru",
        "search:movie" to "Film",
        "search:anime" to "Anime",
        "search:drama" to "Drama",
        "search:action" to "Aksi",
        "search:thriller" to "Thriller",
        "search:horror" to "Horor",
        "search:fantasy" to "Fantasi",
        "search:adventure" to "Petualangan",
        "search:isekai" to "Isekai",
        "search:hindi" to "Dubbed"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = mutableListOf<SearchResponse>()
        when (request.data) {
            "timeline" -> {
                val timelineUrl = "$apiUrl/intl/gateway/web/v2/ogv/timeline?s_locale=id_ID&platform=web"
                val res = app.get(timelineUrl, headers = headers, cookies = cookies).parsedSafe<TimelineResult>()
                res?.data?.items?.forEach dayLoop@{ day ->
                    day.cards?.forEach cardLoop@{ card ->
                        val title = card.title ?: return@cardLoop
                        val seasonId = card.seasonId ?: return@cardLoop
                        val cover = card.cover
                        items.add(newAnimeSearchResponse(title, seasonId, TvType.Anime) { this.posterUrl = cover })
                    }
                }
            }
            "trending", "popular", "recommend" -> {
                val keyword = when(request.data) {
                    "trending" -> "2024"
                    "popular" -> "anime"
                    else -> "action"
                }
                val searchUrl = "$apiUrl/intl/gateway/web/v2/search_result?keyword=$keyword&s_locale=id_ID&limit=30"
                val res = app.get(searchUrl, headers = headers, cookies = cookies).parsedSafe<SearchResult>()
                res?.data?.modules?.forEach moduleLoop@{ module ->
                    module.data?.items?.forEach itemLoop@{ item ->
                        val title = item.title ?: return@itemLoop
                        val seasonId = item.seasonId ?: return@itemLoop
                        items.add(newAnimeSearchResponse(title, seasonId, TvType.Anime) {
                            this.posterUrl = item.cover ?: item.poster ?: item.horizontalCover
                        })
                    }
                }
            }
        }
        return newHomePageResponse(request.name, items.distinctBy { it.url })
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$apiUrl/intl/gateway/web/v2/search_result?keyword=$query&s_locale=id_ID&limit=20"
        return try {
            val res = app.get(url, headers = headers, cookies = cookies).parsedSafe<SearchResult>()
            res?.data?.modules?.flatMap moduleLoop@{ module ->
                module.data?.items?.mapNotNull itemLoop@{ item ->
                    val title = item.title ?: return@itemLoop null
                    val id = item.seasonId ?: return@itemLoop null
                    newAnimeSearchResponse(title, id, TvType.Anime) { this.posterUrl = item.cover }
                } ?: emptyList()
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val seasonId = url.substringAfterLast("/").filter { it.isDigit() }.ifEmpty { url }
        val seasonApiUrl = "$apiUrl/intl/gateway/v2/ogv/view/app/season?season_id=$seasonId&platform=web&s_locale=id_ID"
        val res = app.get(seasonApiUrl, headers = headers, cookies = cookies).parsedSafe<SeasonResult>() ?: throw ErrorLoadingException("Failed to load")
        val result = res.result ?: throw ErrorLoadingException("No result")
        val title = result.title ?: "Unknown"
        val poster = result.cover
        val description = result.evaluate
        val episodes = mutableListOf<Episode>()

        result.modules?.forEach moduleLoop@{ module ->
            module.data?.episodes?.forEach epLoop@{ ep ->
                val epNum = ep.index?.toIntOrNull() ?: ep.title?.toIntOrNull()
                val epName = if (ep.title?.toIntOrNull() != null) "Episode ${ep.title}" else (ep.title ?: "Episode ${epNum ?: "?"}")
                episodes.add(newEpisode(LoadData(ep.id.toString(), seasonId).toJson()) {
                    this.name = epName
                    this.episode = epNum ?: (episodes.size + 1)
                    this.posterUrl = ep.cover
                })
            }
        }

        result.episodes?.forEach epLoop@{ ep ->
            if (episodes.none { parseJson<LoadData>(it.data).epId == ep.id.toString() }) {
                val epNum = ep.index?.toIntOrNull() ?: ep.title?.toIntOrNull()
                val epName = if (ep.title?.toIntOrNull() != null) "Episode ${ep.title}" else (ep.title ?: "Episode ${epNum ?: "?"}")
                episodes.add(newEpisode(LoadData(ep.id.toString(), seasonId).toJson()) {
                    this.name = epName
                    this.episode = epNum ?: (episodes.size + 1)
                    this.posterUrl = ep.cover
                })
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    @OptIn(com.lagradost.cloudstream3.Prerelease::class)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = try { parseJson<LoadData>(data) } catch (_: Exception) { LoadData(data, "") }
        val epId = loadData.epId
        var foundLinks = false

        try {
            val primaryUrl = "$apiUrl/intl/gateway/v2/ogv/playurl?ep_id=$epId&platform=web&qn=64&type=mp4&tf=0&s_locale=id_ID"
            val primaryRes = app.get(primaryUrl, headers = headers, cookies = cookies).parsedSafe<OldPlayResult>()
            val videoInfo = primaryRes?.data?.videoInfo

            if (videoInfo != null) {
                val audioUrl = videoInfo.dashAudio?.firstOrNull()?.baseUrl
                val addedQualities = mutableSetOf<String>()

                primaryRes.data?.durl?.forEach durlLoop@{ durl ->
                    val videoUrl = durl.url ?: return@durlLoop
                    callback(newExtractorLink(this.name, "$name Direct", videoUrl, INFER_TYPE) {
                        this.referer = "https://www.bilibili.tv/"
                        this.quality = Qualities.Unknown.value
                        this.headers = this@Bstation.headers
                    })
                    foundLinks = true
                }

                videoInfo.streamList?.forEach streamLoop@{ stream ->
                    val videoUrl = stream.dashVideo?.baseUrl ?: stream.baseUrl ?: return@streamLoop
                    val quality = stream.streamInfo?.displayDesc ?: "Unknown"
                    if (addedQualities.contains(quality)) return@streamLoop
                    addedQualities.add(quality)

                    if (!audioUrl.isNullOrEmpty()) {
                        val audioFiles = listOf(newAudioFile(audioUrl) {})
                        callback(newExtractorLink(this.name, "$name $quality", videoUrl, INFER_TYPE) {
                            this.referer = "https://www.bilibili.tv/"
                            this.quality = getQualityFromName(quality)
                            this.headers = this@Bstation.headers
                            this.audioTracks = audioFiles
                        })
                        foundLinks = true
                        return@streamLoop
                    }

                    callback(newExtractorLink(this.name, "$name $quality", videoUrl, INFER_TYPE) {
                        this.referer = "https://www.bilibili.tv/"
                        this.quality = getQualityFromName(quality)
                        this.headers = this@Bstation.headers
                    })
                    foundLinks = true
                }
            }
        } catch (_: Exception) {}

        if (!foundLinks) {
            try {
                val fallbackUrl = "$biliintlApiUrl/intl/gateway/web/playurl?ep_id=$epId&s_locale=id_ID&platform=android&qn=64"
                val fallbackRes = app.get(fallbackUrl, headers = headers, cookies = cookies).parsedSafe<BiliIntlPlayResult>()
                val playurl = fallbackRes?.data?.playurl

                playurl?.video?.forEach videoLoop@{ videoItem ->
                    val videoResource = videoItem.videoResource ?: return@videoLoop
                    val videoUrl = videoResource.url ?: return@videoLoop
                    val quality = videoItem.streamInfo?.descWords ?: "${videoResource.height ?: 0}P"
                    callback(newExtractorLink(this.name, "$name $quality", videoUrl, INFER_TYPE) {
                        this.referer = "https://www.bilibili.tv/"
                        this.quality = getQualityFromName(quality)
                        this.headers = this@Bstation.headers
                    })
                    foundLinks = true
                }
            } catch (_: Exception) {}
        }

        try {
            val subApiUrl = "$apiUrl/intl/gateway/v2/ogv/view/app/episode?ep_id=$epId&platform=web&s_locale=id_ID"
            val subRes = app.get(subApiUrl, headers = headers, cookies = cookies).parsedSafe<EpisodeResult>()
            subRes?.data?.subtitles?.forEach subLoop@{ sub ->
                val subUrl = sub.url ?: return@subLoop
                val subTitle = sub.title ?: sub.lang ?: "Unknown"
                val proxyUrl = "$subtitleProxyUrl/?url=${java.net.URLEncoder.encode(subUrl, "UTF-8")}"
                subtitleCallback(SubtitleFile(subTitle, proxyUrl))
            }
        } catch (_: Exception) {}

        return foundLinks
    }

    data class BiliSubtitleJson(@JsonProperty("body") val body: List<BiliSubtitleEntry>?)
    data class BiliSubtitleEntry(@JsonProperty("from") val from: Double?, @JsonProperty("to") val to: Double?, @JsonProperty("content") val content: String?)
    data class LoadData(val epId: String, val seasonId: String?)
    data class TimelineResult(@JsonProperty("data") val data: TimelineData?)
    data class TimelineData(@JsonProperty("items") val items: List<TimelineDay>?)
    data class TimelineDay(@JsonProperty("cards") val cards: List<TimelineCard>?)
    data class TimelineCard(@JsonProperty("title") val title: String?, @JsonProperty("cover") val cover: String?, @JsonProperty("season_id") val seasonId: String?)
    data class SearchResult(@JsonProperty("data") val data: SearchData?)
    data class SearchData(@JsonProperty("modules") val modules: List<SearchModule>?)
    data class SearchModule(@JsonProperty("data") val data: SearchModuleData?)
    data class SearchModuleData(@JsonProperty("items") val items: List<SearchItem>?)
    data class SearchItem(@JsonProperty("title") val title: String?, @JsonProperty("season_id") val seasonId: String?, @JsonProperty("cover") val cover: String?, @JsonProperty("poster") val poster: String?, @JsonProperty("horizontal_cover") val horizontalCover: String?)
    data class SeasonResult(@JsonProperty("result") val result: SeasonData?)
    data class SeasonData(@JsonProperty("title") val title: String?, @JsonProperty("cover") val cover: String?, @JsonProperty("evaluate") val evaluate: String?, @JsonProperty("episodes") val episodes: List<EpisodeData>?, @JsonProperty("modules") val modules: List<ModuleData>?)
    data class ModuleData(@JsonProperty("data") val data: ModuleEpisodes?)
    data class ModuleEpisodes(@JsonProperty("episodes") val episodes: List<EpisodeData>?)
    data class EpisodeData(@JsonProperty("id") val id: Long?, @JsonProperty("title") val title: String?, @JsonProperty("index_show") val index: String?, @JsonProperty("cover") val cover: String?, @JsonProperty("subtitles") val subtitles: List<SubtitleData>?)
    data class SubtitleData(@JsonProperty("lang") val lang: String?, @JsonProperty("title") val title: String?, @JsonProperty("url") val url: String?)
    data class EpisodeResult(@JsonProperty("data") val data: EpisodeApiData?)
    data class EpisodeApiData(@JsonProperty("subtitles") val subtitles: List<SubtitleData>?)
    data class OldPlayResult(@JsonProperty("data") val data: OldPlayData?)
    data class OldPlayData(@JsonProperty("video_info") val videoInfo: OldVideoInfo?, @JsonProperty("durl") val durl: List<OldDurl>?)
    data class OldVideoInfo(@JsonProperty("stream_list") val streamList: List<OldStream>?, @JsonProperty("dash_audio") val dashAudio: List<OldDashAudio>?)
    data class OldDashAudio(@JsonProperty("base_url") val baseUrl: String?)
    data class OldStream(@JsonProperty("stream_info") val streamInfo: OldStreamInfo?, @JsonProperty("dash_video") val dashVideo: OldDashVideo?, @JsonProperty("base_url") val baseUrl: String?)
    data class OldStreamInfo(@JsonProperty("display_desc") val displayDesc: String?)
    data class OldDashVideo(@JsonProperty("base_url") val baseUrl: String?)
    data class OldDurl(@JsonProperty("url") val url: String?)
    data class BiliIntlPlayResult(@JsonProperty("code") val code: Int?, @JsonProperty("data") val data: BiliIntlData?)
    data class BiliIntlData(@JsonProperty("playurl") val playurl: BiliIntlPlayurl?)
    data class BiliIntlPlayurl(@JsonProperty("duration") val duration: Long?, @JsonProperty("video") val video: List<BiliIntlVideo>?, @JsonProperty("audio_resource") val audioResource: List<BiliIntlAudio>?)
    data class BiliIntlVideo(@JsonProperty("video_resource") val videoResource: BiliIntlVideoResource?, @JsonProperty("stream_info") val streamInfo: BiliIntlStreamInfo?, @JsonProperty("audio_quality") val audioQuality: Int?)
    data class BiliIntlVideoResource(@JsonProperty("url") val url: String?, @JsonProperty("bandwidth") val bandwidth: Int?, @JsonProperty("codecs") val codecs: String?, @JsonProperty("width") val width: Int?, @JsonProperty("height") val height: Int?)
    data class BiliIntlStreamInfo(@JsonProperty("quality") val quality: Int?, @JsonProperty("desc_words") val descWords: String?)
    data class BiliIntlAudio(@JsonProperty("url") val url: String?, @JsonProperty("bandwidth") val bandwidth: Int?, @JsonProperty("codecs") val codecs: String?, @JsonProperty("quality") val quality: Int?)
}

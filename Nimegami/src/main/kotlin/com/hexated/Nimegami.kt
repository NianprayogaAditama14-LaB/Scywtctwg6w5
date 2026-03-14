package com.hexated

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

class Nimegami : MainAPI() {

    override var mainUrl = "https://nimegami.id"
    override var name = "Nimegami"
    override val hasMainPage = true
    override var lang = "id"

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    companion object {

        fun getType(t: String): TvType {
            return when {
                t.contains("Tv", true) -> TvType.Anime
                t.contains("Movie", true) -> TvType.AnimeMovie
                t.contains("OVA", true) -> TvType.OVA
                else -> TvType.Anime
            }
        }

        fun getStatus(t: String?): ShowStatus {
            return if (t?.contains("On-Going", true) == true)
                ShowStatus.Ongoing
            else
                ShowStatus.Completed
        }
    }

    override val mainPage = mainPageOf(
        "" to "Updated Anime",
        "/type/tv" to "Anime",
        "/type/movie" to "Movie",
        "/type/ona" to "ONA",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val document =
            app.get("$mainUrl${request.data}/page/$page").document

        val home = document.select(
            "div.post-article article, div.archive article"
        ).mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            HomePageList(request.name, home),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): AnimeSearchResponse? {

        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val title = this.selectFirst("h2 a")?.text() ?: return null

        val posterUrl =
            (this.selectFirst("noscript img")
                ?: this.selectFirst("img"))?.attr("src")

        val episode =
            this.selectFirst("ul li:contains(Episode)")
                ?.ownText()
                ?.filter { it.isDigit() }
                ?.toIntOrNull()

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            addSub(episode)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {

        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..2) {

            val res = app.get(
                "$mainUrl/page/$i/?s=$query&post_type=post"
            ).document.select("div.archive article")
                .mapNotNull { it.toSearchResult() }

            searchResponse.addAll(res)
        }

        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {

        val document = app.get(url).document
        val table = document.select("div#Info table tbody")

        val title = table.getContent("Judul").text()

        val poster =
            document.selectFirst("div.coverthumbnail img")
                ?.attr("src")

        val bgPoster =
            document.selectFirst("div.thumbnail-a img")
                ?.attr("src")

        val tags =
            table.getContent("Kategori").select("a")
                .map { it.text() }

        val year =
            table.getContent("Musim / Rilis")
                .text()
                .filter { it.isDigit() }
                .toIntOrNull()

        val status =
            getStatus(document.selectFirst("h1")?.text())

        val type =
            getType(table.getContent("Type").text())

        val description =
            document.select("div#Sinopsis p")
                .text()
                .trim()

        val trailer =
            document.selectFirst("div#Trailer iframe")
                ?.attr("src")

        val episodes =
            document.select("div.list_eps_stream li.select-eps")
                .mapNotNull { ep ->

                    val data = ep.attr("data")
                    if (data.isNullOrBlank()) return@mapNotNull null

                    val episode =
                        Regex("Episode\\s?(\\d+)")
                            .find(ep.text())
                            ?.groupValues?.getOrNull(1)
                            ?.toIntOrNull()

                    newEpisode(data) {
                        this.episode = episode
                    }
                }

        val tracker =
            APIHolder.getTracker(
                listOf(title),
                TrackerType.getTypes(type),
                year,
                true
            )

        return newAnimeLoadResponse(title, url, type) {

            posterUrl = tracker?.image ?: poster
            backgroundPosterUrl = tracker?.cover ?: bgPoster

            this.year = year
            showStatus = status
            plot = description
            this.tags = tags

            addEpisodes(DubStatus.Subbed, episodes)

            addTrailer(trailer)
            addMalId(tracker?.malId)
            addAniListId(tracker?.aniId?.toIntOrNull())
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        if (data.isBlank()) return false

        val decoded = try {
            base64Decode(data)
        } catch (e: Exception) {
            data
        }

        val clean = decoded
            .replace("\\/", "/")
            .trim()

        val sources =
            tryParseJson<ArrayList<Sources>>(clean)
                ?: return false

        sources.forEach { source ->
            source.url?.forEach { url ->

                val fixed = url.replace("\\/", "/")

                if (fixed.contains("dlgan.space")) {
                    extractVideo(fixed, source.format, callback)
                }
            }
        }

        return true
    }

    private suspend fun extractVideo(
        url: String,
        quality: String?,
        callback: (ExtractorLink) -> Unit
    ) {

        val id =
            Regex("id=([a-zA-Z0-9]+)")
                .find(url)?.groupValues?.getOrNull(1)

        val name =
            Regex("name=([^&]+)")
                .find(url)?.groupValues?.getOrNull(1)

        if (id == null || name == null) return

        val api =
            "https://api.dlgan.space/api.php?id=$id&name=$name"

        val json =
            app.get(api).parsedSafe<Map<String, Any>>() ?: return

        val stream =
            json["stream_url"] as? String ?: return

        val q = when {
            name.contains("360") -> 360
            name.contains("480") -> 480
            name.contains("720") -> 720
            name.contains("1080") -> 1080
            else -> getQualityFromName(quality)
        }

        callback.invoke(
            newExtractorLink(
                "Nimegami",
                "Nimegami ${q}p",
                stream,
                ExtractorLinkType.VIDEO
            ) {
                quality = q
                headers = mapOf(
                    "Referer" to mainUrl
                )
            }
        )
    }

    private fun Elements.getContent(css: String): Elements {
        return this.select("tr:contains($css) td:last-child")
    }

    data class Sources(
        @JsonProperty("format")
        val format: String? = null,

        @JsonProperty("url")
        val url: ArrayList<String>? = arrayListOf()
    )
}

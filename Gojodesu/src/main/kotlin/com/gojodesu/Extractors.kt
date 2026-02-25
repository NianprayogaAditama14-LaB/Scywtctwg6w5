package com.gojodesu

import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

open class EmturbovidExtractor : ExtractorApi() {

    override var name = "Gojodesu"
    override var mainUrl = "https://emturbovid.com"
    override val requiresReferer = true

    private val UA =
        try { USER_AGENT } catch (_: Throwable) {
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

    private fun absoluteUrl(base: String, value: String): String {
        val raw = value.trim()
        return when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.startsWith("//") -> "https:$raw"
            else -> runCatching {
                java.net.URI(base).resolve(raw).toString()
            }.getOrDefault(raw)
        }
    }

    private fun parseVariants(master: String, masterUrl: String): List<Pair<String, Int>> {
        val lines = master.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        val out = ArrayList<Pair<String, Int>>()

        for (i in lines.indices) {
            val line = lines[i]
            if (!line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) continue

            val next = lines.getOrNull(i + 1) ?: continue
            if (next.startsWith("#")) continue

            val height = Regex(
                """RESOLUTION\s*=\s*\d+\s*x\s*(\d+)""",
                RegexOption.IGNORE_CASE
            ).find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()

            val quality = height ?: Qualities.Unknown.value
            out += absoluteUrl(masterUrl, next) to quality
        }

        return out
    }

    private fun findIds(text: String): Pair<String, String>? {
        val vid = Regex(
            """videoID["']?\s*[:=]\s*["']?([a-zA-Z0-9]+)""",
            RegexOption.IGNORE_CASE
        ).find(text)?.groupValues?.getOrNull(1)

        val uid = Regex(
            """userID["']?\s*[:=]\s*["']?(\d+)""",
            RegexOption.IGNORE_CASE
        ).find(text)?.groupValues?.getOrNull(1)

        return if (!vid.isNullOrBlank() && !uid.isNullOrBlank()) {
            vid to uid
        } else null
    }

    override suspend fun getUrl(
        url: String,
        referer: String?
    ): List<ExtractorLink>? {

        val pageRef = referer ?: "$mainUrl/"

        val headers = mapOf(
            "Referer" to pageRef,
            "User-Agent" to UA,
            "Accept" to "*/*"
        )

        val page = app.get(url, referer = pageRef, headers = headers)
        val pageText = page.text

        val masterRaw = Regex(
            """\bvar\s+urlPlay\s*=\s*['"]([^'"]+)['"]""",
            RegexOption.IGNORE_CASE
        ).find(pageText)?.groupValues?.getOrNull(1)
            ?: return null

        val masterUrl = absoluteUrl(page.url, masterRaw)

        // Trigger SPTVP bypass (optional but penting buat beberapa video)
        findIds(pageText)?.let { (vid, uid) ->
            runCatching {
                app.get(
                    "https://ver03.sptvp.com/watch?videoID=$vid&userID=$uid",
                    referer = pageRef,
                    headers = headers
                )
            }
            runCatching {
                app.get(
                    "https://ver02.sptvp.com/watch?videoID=$vid&userID=$uid",
                    referer = pageRef,
                    headers = headers
                )
            }
        }

        val masterText = runCatching {
            app.get(masterUrl, referer = pageRef, headers = headers).text
        }.getOrNull().orEmpty()

        val variants = if (masterText.trimStart().startsWith("#EXTM3U")) {
            parseVariants(masterText, masterUrl)
        } else emptyList()

        if (variants.isNotEmpty()) {
            return variants
                .distinctBy { it.first }
                .sortedByDescending { it.second }
                .map { (variantUrl, quality) ->
                    newExtractorLink(
                        source = Gojodesu,
                        name = "$name ${quality}p",
                        url = variantUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = pageRef
                        this.headers = headers
                        this.quality = quality
                    }
                }
        }

        return listOf(
            newExtractorLink(
                source = name,
                name = name,
                url = masterUrl,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = pageRef
                this.headers = headers
                this.quality = Qualities.Unknown.value
            }
        )
    }
}
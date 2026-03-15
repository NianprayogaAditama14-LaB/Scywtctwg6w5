open class Berkasdrive : ExtractorApi() {
    override val name = "Berkasdrive"
    override val mainUrl = "https://dl.berkasdrive.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url, referer = referer).document
        val video = res.select("video#player source").attr("src")

        callback.invoke(
            newExtractorLink(
                this.name,
                this.name,
                video,
                INFER_TYPE
            ) {
                this.referer = "$mainUrl/"
            }
        )

    }

}

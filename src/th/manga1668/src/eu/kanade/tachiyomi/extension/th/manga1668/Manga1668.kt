package eu.kanade.tachiyomi.extension.th.manga1668

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Manga1668 : KeiSource() {

    protected override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 2, period = 1.seconds)

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(page, "popular")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(page, "update")

    private suspend fun getMangaList(page: Int, order: String): MangasPage {
        val url = "$baseUrl/manga/".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("order", order)
            .build()
        return parseMangaList(client.get(url, headers).asJsoup())
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/manga/".toHttpUrl().newBuilder()
            .addQueryParameter("title", query)
            .addQueryParameter("page", page.toString())
            .build()
        return parseMangaList(client.get(url, headers).asJsoup())
    }

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select(".listupd .bs .bsx").mapNotNull { element ->
            val link = element.selectFirst("a[href*=/manga/]") ?: return@mapNotNull null
            val title = element.selectFirst(".tt")?.text()
                ?: link.attr("title").takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null

            SManga.create().apply {
                this.title = title
                thumbnail_url = element.selectFirst("img")?.imageUrl()
                setUrlWithoutDomain(link.absUrl("href"))
            }
        }
        val hasNextPage = document.selectFirst("div.hpage a.r, div.pagination a.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.size != 2 || segments.first() != "manga") return null

        return parseMangaDetails(client.get(url, headers).asJsoup()).apply {
            this.url = "/manga/${segments.last()}/"
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), headers).asJsoup()
        val details = parseMangaDetails(document).apply { url = manga.url }
        val chapterList = document.select("#chapterlist li").mapNotNull(::parseChapter)
        return SMangaUpdate(manga = details, chapters = chapterList)
    }

    private fun parseMangaDetails(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.entry-title")!!.text()
        thumbnail_url = document.selectFirst(".thumb img")?.imageUrl()
        author = document.infoValue("Author")
        artist = document.infoValue("Artist")

        val genres = document.select(".seriestugenre a").map { it.text() }.toMutableList()
        document.infoValue("Type")?.let(genres::add)
        if (document.selectFirst(".thumb .colored") != null) genres.add("Color")
        genre = genres.distinct().joinToString()

        status = when (document.infoValue("Status")?.lowercase()) {
            "ongoing", "กำลังดำเนินการ" -> SManga.ONGOING
            "completed", "finished", "จบแล้ว" -> SManga.COMPLETED
            "cancelled", "canceled", "dropped" -> SManga.CANCELLED
            "hiatus", "on hold" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }

        val summary = document.selectFirst(".entry-content[itemprop=description]")?.text().orEmpty()
        val alternative = document.infoValue("Alternative")
        description = buildString {
            if (summary.isNotEmpty()) append(summary)
            if (!alternative.isNullOrEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("ชื่ออื่น: ").append(alternative)
            }
        }.ifEmpty { null }
    }

    private fun parseChapter(element: Element): SChapter? {
        val link = element.selectFirst("a[href]") ?: return null
        val name = element.selectFirst(".chapternum")?.text() ?: return null
        return SChapter.create().apply {
            this.name = name
            date_upload = dateFormat.tryParseDate(
                element.selectFirst(".chapterdate")?.text(),
                THAI_ZONE,
            )
            setUrlWithoutDomain(link.absUrl("href"))
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), headers).asJsoup()
        return document.select("#readerarea img").mapIndexedNotNull { index, image ->
            image.imageUrl()?.let { Page(index, imageUrl = it) }
        }
    }

    private fun Document.infoValue(label: String): String? = select(".infotable tr")
        .firstOrNull { row -> row.selectFirst("td")?.text()?.equals(label, ignoreCase = true) == true }
        ?.select("td")
        ?.getOrNull(1)
        ?.text()
        ?.takeIf { it.isNotEmpty() }

    private fun Element.imageUrl(): String? = absUrl("src").takeIf { it.isNotEmpty() }
        ?: absUrl("data-src").takeIf { it.isNotEmpty() }

    companion object {
        private val THAI_ZONE = ZoneId.of("Asia/Bangkok")
        private val dateFormat = DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.ENGLISH)
    }
}

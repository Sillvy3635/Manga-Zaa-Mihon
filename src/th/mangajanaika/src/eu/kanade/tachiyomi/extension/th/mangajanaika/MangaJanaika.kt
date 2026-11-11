package eu.kanade.tachiyomi.extension.th.mangajanaika

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MangaJanaika : KeiSource() {

    protected override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 2, period = 1.seconds)

    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)
        val document = client.get(baseUrl, headers).asJsoup()
        val mangas = document.select(".bg-top .col-sm-12.mt-2.mb-2").mapNotNull { row ->
            val link = row.selectFirst(".sr-title-sm a[href]") ?: return@mapNotNull null
            SManga.create().apply {
                title = link.text()
                thumbnail_url = row.selectFirst(".img-top-image")?.backgroundImage()
                setUrlWithoutDomain(link.absUrl("href"))
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        val document = client.get(url, headers).asJsoup()
        return MangasPage(parseCards(document), document.hasNextPage())
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (page > 1 || query.isBlank()) return MangasPage(emptyList(), false)
        val body = FormBody.Builder().add("keysearch", query).build()
        val document = client.post("$baseUrl/controller/search.php", headers, body).asJsoup()
        return MangasPage(parseCards(document), false)
    }

    private fun parseCards(document: Document): List<SManga> = document.select(".item-grid .item").mapNotNull { item ->
        val link = item.selectFirst("a[href]") ?: return@mapNotNull null
        val title = item.selectFirst(".title h2")?.text()?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        SManga.create().apply {
            this.title = title
            thumbnail_url = item.selectFirst(".poster")?.backgroundImage()
            setUrlWithoutDomain(link.absUrl("href"))
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.count { it.isNotEmpty() } != 1) return null
        return parseDetails(client.get(url, headers).asJsoup()).apply { this.url = url.encodedPath }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val mangaUrl = getMangaUrl(manga).toHttpUrl()
        var document = client.get(mangaUrl, headers).asJsoup()
        val details = parseDetails(document).apply { url = manga.url }
        val allChapters = mutableListOf<SChapter>()
        var page = 1
        do {
            allChapters += document.select(".ep-row a[href]").map { link ->
                SChapter.create().apply {
                    name = link.text().trim()
                    chapter_number = CHAPTER_NUMBER.find(name)?.groupValues?.get(1)?.toFloatOrNull() ?: -1f
                    setUrlWithoutDomain(link.absUrl("href"))
                }
            }
            if (!document.hasNextPage() || page >= MAX_CHAPTER_PAGES) break
            page++
            val nextUrl = mangaUrl.newBuilder().setQueryParameter("page", page.toString()).build()
            document = client.get(nextUrl, headers).asJsoup()
        } while (true)
        return SMangaUpdate(manga = details, chapters = allChapters.distinctBy { it.url }.sortedByDescending { it.chapter_number })
    }

    private fun parseDetails(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1")?.ownText().orEmpty()
        thumbnail_url = document.selectFirst("img.img-thumbnail")?.absUrl("src")
        artist = document.infoValue("ศิลปิน")
        genre = document.infoLinks("ประเภท").joinToString()
        val descriptionText = document.selectFirst("meta[name=description]")?.attr("content")
        description = descriptionText?.takeUnless { it.isBlank() || it == "No Story" }
        status = if (document.infoLinks("หมวดหมู่").any { it.contains("จบแล้ว") }) SManga.COMPLETED else SManga.UNKNOWN
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), headers).asJsoup()
        val pages = document.select("#manga-content img").mapIndexedNotNull { index, image ->
            val imageUrl = image.absUrl("data-src").ifEmpty { image.absUrl("src") }
            imageUrl.takeIf { it.isNotEmpty() }?.let { Page(index, imageUrl = it) }
        }
        check(pages.isNotEmpty()) { "Manga Janaika: ไม่พบรูปในตอน ${chapter.url}" }
        return pages
    }

    private fun Document.hasNextPage(): Boolean = select("a[href*='?page=']")
        .any { it.text().contains("Next", ignoreCase = true) && !it.hasClass("disabled") }

    private fun Document.infoValue(label: String): String? = select(".row.mt-3")
        .firstOrNull { it.selectFirst(".col-2")?.text() == label }
        ?.selectFirst(".col-9")
        ?.text()
        ?.takeIf { it.isNotBlank() }

    private fun Document.infoLinks(label: String): List<String> = select(".row.mt-3")
        .firstOrNull { it.selectFirst(".col-2")?.text() == label }
        ?.select(".col-9 a")
        ?.map { it.text() }
        .orEmpty()

    private fun Element.backgroundImage(): String? = attr("style")
        .substringAfter("url(", "")
        .substringBefore(')')
        .trim('\'', '"', ' ')
        .takeIf { it.isNotEmpty() }

    companion object {
        private const val MAX_CHAPTER_PAGES = 100
        private val CHAPTER_NUMBER = Regex("""(?:ตอนที่|EP|Ch\.?)\s*([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)
    }
}

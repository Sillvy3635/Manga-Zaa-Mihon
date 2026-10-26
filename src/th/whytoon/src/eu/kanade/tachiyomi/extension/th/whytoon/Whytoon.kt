package eu.kanade.tachiyomi.extension.th.whytoon

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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Whytoon : KeiSource() {

    protected override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 2, period = 1.seconds)

    override suspend fun getPopularManga(page: Int): MangasPage = getList("popular", page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getList("browse", page)

    private suspend fun getList(section: String, page: Int, query: String? = null): MangasPage {
        val path = if (page == 1) section else "$section/page/$page"
        val url = "$baseUrl/$path".toHttpUrl().newBuilder().apply {
            if (!query.isNullOrBlank()) addQueryParameter("q", query)
        }.build()
        val document = client.get(url, headers).asJsoup()
        val mangas = document.select("a.group.block[href^=/content/]").mapNotNull { link ->
            val title = link.selectFirst("h3")?.text()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            SManga.create().apply {
                this.title = title
                thumbnail_url = link.selectFirst("img[src*=/content/]")?.absUrl("src")
                setUrlWithoutDomain(link.absUrl("href"))
            }
        }
        return MangasPage(mangas, mangas.size >= 20)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getList("browse", page, query)

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.size != 2 || segments.first() != "content") return null
        return parseDetails(client.get(url, headers).asJsoup()).apply { this.url = url.encodedPath }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), headers).asJsoup()
        val details = parseDetails(document).apply { url = manga.url }
        val episodeData = document.html().substringAfter(EPISODES_MARKER, "").substringBefore(']')
        val chapterList = EPISODE_NUMBER.findAll(episodeData).map { match ->
            val number = match.groupValues[1]
            SChapter.create().apply {
                name = "ตอนที่ $number"
                chapter_number = number.toFloatOrNull() ?: -1f
                url = "${manga.url.trimEnd('/')}/$number"
            }
        }.toList().asReversed()
        return SMangaUpdate(manga = details, chapters = chapterList)
    }

    private fun parseDetails(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1")?.text().orEmpty()
        thumbnail_url = document.selectFirst("img[src*=/content/]")?.absUrl("src")
        genre = document.select("a[href^=/browse/]").map { it.text() }.distinct().joinToString()
        description = document.selectFirst("h2:containsOwn(เรื่องย่อ) + div")?.text()
        val statusText = document.selectFirst("span:containsOwn(สถานะ:) + div")?.text().orEmpty()
        status = when {
            statusText.contains("จบแล้ว") -> SManga.COMPLETED
            statusText.contains("กำลังดำเนินการ") -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), headers).asJsoup()
        val imageData = document.html().substringAfter(IMAGES_MARKER, "").substringBefore(']')
        val images = IMAGE_PATH.findAll(imageData).map { it.value }.distinct().toList()
        check(images.isNotEmpty()) { "Whytoon: ไม่พบรูปในตอน ${chapter.url}" }
        return images.mapIndexed { index, path -> Page(index, imageUrl = "$IMAGE_CDN/$path") }
    }

    companion object {
        private const val EPISODES_MARKER = "episodes\\\":["
        private const val IMAGES_MARKER = "images\\\":["
        private const val IMAGE_CDN = "https://img.whytoon.com"
        private val EPISODE_NUMBER = Regex("""\\"no\\":([0-9.]+)""")
        private val IMAGE_PATH = Regex("""content/[^"\\]+\.(?:webp|png|jpe?g)""", RegexOption.IGNORE_CASE)
    }
}

package eu.kanade.tachiyomi.extension.th.manga1668

import eu.kanade.tachiyomi.multisrc.madara.MadaraBase
import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Manga1668 : MadaraNoAjax() {

    override val chapterMode = MadaraBase.ChapterMode.MangaAjax

    override val filterNonMangaItems = false

    override val chapterDateFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.US)

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder =
        rateLimit(permits = 2, period = 1.seconds)

    override suspend fun getPopularManga(page: Int): MangasPage = getArchive(page, "popular")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getArchive(page, "update")

    private suspend fun getArchive(page: Int, order: String): MangasPage {
        val url = "$baseUrl/manga/".toHttpUrl().newBuilder()
            .addQueryParameter("order", order)
            .apply {
                if (page > 1) addQueryParameter("page", page.toString())
            }
            .build()
        val document = client.get(url, headers).asJsoup()
        val mangas = parseArchive(document)
        val hasNextPage = document.select("a[href]").any { link ->
            link.absUrl("href").toHttpUrlOrNull()
                ?.queryParameter("page")
                ?.toIntOrNull()
                ?.let { it > page } == true
        }
        return MangasPage(mangas, hasNextPage)
    }

    override fun parseArchive(document: Document): List<SManga> =
        document.select(archiveSelector()).mapNotNull { element ->
            val link = element.selectFirst(archiveUrlSelector) ?: return@mapNotNull null
            val href = link.attr("abs:href").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val path = href.toHttpUrl().encodedPath

            SManga.create().apply {
                url = path
                title = link.text()
                thumbnail_url = element.selectFirst("img")?.let { processThumbnail(imageFromElement(it), true) }
                memo = mangaMemo(path, emptyList())
            }
        }
}

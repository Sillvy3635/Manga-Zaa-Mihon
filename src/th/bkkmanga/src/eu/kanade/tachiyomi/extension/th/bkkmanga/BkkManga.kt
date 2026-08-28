package eu.kanade.tachiyomi.extension.th.bkkmanga

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.multisrc.madara.MadaraBase
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document

@Source
abstract class BkkManga : MadaraNoAjax() {

    override val chapterMode = MadaraBase.ChapterMode.AdminAjax

    override fun parseArchive(document: Document): List<SManga> = document.select(archiveSelector()).mapNotNull { element ->
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

    override fun parseChapterList(document: Document, mangaPath: String): List<SChapter> {
        val siteUrl = baseUrl.toHttpUrl()
        val pathPrefix = siteUrl.resolve(mangaPath)?.encodedPath?.trimEnd('/')?.plus('/') ?: return emptyList()

        return document.select("a[href]").mapNotNull { link ->
            val chapterUrl = siteUrl.resolve(link.attr("href"))
                ?: link.attr("href").toHttpUrlOrNull()
                ?: return@mapNotNull null
            val path = chapterUrl.encodedPath
            if (chapterUrl.host != siteUrl.host || !path.startsWith(pathPrefix, ignoreCase = true)) {
                return@mapNotNull null
            }

            SChapter.create().apply {
                url = path
                name = link.text()
            }
        }.distinctBy(SChapter::url)
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl.toHttpUrl().resolve(chapter.url)?.toString()
        ?: error("Invalid chapter URL: ${chapter.url}")
}

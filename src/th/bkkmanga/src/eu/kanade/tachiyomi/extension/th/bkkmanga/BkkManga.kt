package eu.kanade.tachiyomi.extension.th.bkkmanga

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class BkkManga : MadaraNoAjax() {

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

    override fun chapterFromElement(element: Element, mangaPath: String): SChapter? {
        val link = element.selectFirst(chapterUrlSelector) ?: return null
        val href = link.attr("abs:href").takeIf(String::isNotBlank) ?: return null

        return SChapter.create().apply {
            url = href.toHttpUrl().encodedPath
            name = link.text()
            date_upload = parseChapterDate(
                element.selectFirst("img:not(.thumb)")?.attr("alt")?.takeIf(String::isNotBlank)
                    ?: element.selectFirst("span a")?.attr("title")?.takeIf(String::isNotBlank)
                    ?: element.selectFirst(chapterDateSelector)?.text(),
            )
        }
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl.toHttpUrl().resolve(chapter.url)?.toString()
        ?: error("Invalid chapter URL: ${chapter.url}")
}

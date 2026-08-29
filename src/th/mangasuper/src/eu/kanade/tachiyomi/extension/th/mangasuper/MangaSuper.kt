package eu.kanade.tachiyomi.extension.th.mangasuper

import eu.kanade.tachiyomi.multisrc.madara.MadaraBase
import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter

@Source
abstract class MangaSuper : MadaraNoAjax() {

    override val chapterMode = MadaraBase.ChapterMode.MangaAjax

    override val chapterDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu")
}

package eu.kanade.tachiyomi.extension.th.mangasuper

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.MadaraBase
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter

@Source
abstract class MangaSuper : Madara() {

    override val chapterMode = MadaraBase.ChapterMode.MangaAjax

    override val chapterDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu")
}

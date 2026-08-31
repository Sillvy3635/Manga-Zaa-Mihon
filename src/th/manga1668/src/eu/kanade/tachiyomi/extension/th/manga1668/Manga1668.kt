package eu.kanade.tachiyomi.extension.th.manga1668

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.MadaraBase
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Manga1668 : Madara() {

    override val chapterMode = MadaraBase.ChapterMode.MangaAjax

    override val filterNonMangaItems = false

    override val chapterDateFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.US)

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder =
        rateLimit(permits = 2, period = 1.seconds)
}
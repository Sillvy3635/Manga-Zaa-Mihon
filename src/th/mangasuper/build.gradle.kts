import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaSuper"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "madara"

    source {
        baseUrl = "https://mangasuper.com"
        lang = "th"
    }

    deeplink {
        path("/manga/..*")
    }
}

import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "1668Manga"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "madara"

    source {
        baseUrl = "https://1668manga.com"
        lang = "th"
    }

    deeplink {
        path("/manga/..*")
    }
}
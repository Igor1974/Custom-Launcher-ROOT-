package com.deepnight.launcher

data class ProviderConfig(
    val authority: String,
    val uriPath: String,
    val queryParam: String,
    val idColumn: String,
    val titleColumn: String,
    val posterColumn: String
)

object AppSearchConfig {
    val providerConfigs = mapOf(
        "com.lazycatsoftware.lmd" to listOf(
            ProviderConfig(
                authority = "com.lazycatsoftware.lmd.tvsearch",
                uriPath = "search_suggest_query",
                queryParam = "query",
                idColumn = "suggest_intent_data",
                titleColumn = "suggest_text_1",
                posterColumn = "suggest_icon_1"
            ),
            ProviderConfig(
                authority = "com.lazycatsoftware.lmd.search.SuggestionProvider",
                uriPath = "search_suggest_query",
                queryParam = "query",
                idColumn = "suggest_intent_data",
                titleColumn = "suggest_text_1",
                posterColumn = "suggest_icon_1"
            )
        ),
        "ru.kinopoisk.tv" to listOf(
            ProviderConfig(
                authority = "ru.kinopoisk.tv.search.kinopoisk.tv",
                uriPath = "search_suggest_query",
                queryParam = "query",
                idColumn = "suggest_intent_data",
                titleColumn = "suggest_text_1",
                posterColumn = "suggest_icon_1"
            ),
            ProviderConfig(
                authority = "ru.kinopoisk.tv.search.SuggestionProvider",
                uriPath = "search_suggest_query",
                queryParam = "query",
                idColumn = "suggest_intent_data",
                titleColumn = "suggest_text_1",
                posterColumn = "suggest_icon_1"
            )
        ),
        "ru.ivi.client" to listOf(
            ProviderConfig(
                authority = "ru.ivi.client.tv.data.search",
                uriPath = "search_suggest_query",
                queryParam = "query",
                idColumn = "suggest_intent_data",
                titleColumn = "suggest_text_1",
                posterColumn = "suggest_icon_1"
            )
        )
    )

    val deepLinkSchemes = mapOf(
        "ru.ivi.client" to "ruiviclient://video/%s",
        "com.lazycatsoftware.lmd" to "tvhomechannels://com.lazycatsoftware.lmd/article/%s",
        "ru.kinopoisk.tv" to "kpatv://film?filmId=%s",
        "net.gtvbox.vimuhd" to "vimu://play?videoId=%s",
        "ru.mts.mtstv" to "mtstv://play?contentId=%s",
        "com.ertelecom.domrutvstb" to "domrutv://play?contentId=%s",
        "rtb.mobile.android" to "rutube://video/%s",
        "com.teamsmart.videomanager.tv" to "smarttube://play?v=%s",
        "ru.more.play" to "moretv://play?contentId=%s",
        "top.rootu.lamps" to "lampa://details?id=%s",
        "top.rootu.lampa" to "lampa://details?id=%s"
    )
}

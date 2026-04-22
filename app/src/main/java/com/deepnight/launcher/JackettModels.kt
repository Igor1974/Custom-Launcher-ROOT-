package com.deepnight.launcher

import com.google.gson.annotations.SerializedName

data class JackettItem(
    @SerializedName(value = "Title", alternate = ["title"]) val title: String? = null,
    @SerializedName(value = "Size", alternate = ["size"]) val size: Long? = null,
    @SerializedName(value = "Seeders", alternate = ["seeders", "seeds"]) val seeds: Int? = null,
    @SerializedName(value = "Peers", alternate = ["peers"]) val peers: Int? = null,
    @SerializedName(value = "MagnetUri", alternate = ["magneturi", "magnet", "Magnet"]) val magnet: String? = null,
    @SerializedName(value = "Link", alternate = ["link"]) val link: String? = null,
    @SerializedName(value = "PublishDate", alternate = ["publishdate", "date"]) val date: String? = null,
    @SerializedName(value = "Tracker", alternate = ["tracker"]) val tracker: String? = null,
    @SerializedName(value = "Poster", alternate = ["poster"]) val poster: String? = null,
    @SerializedName(value = "Details", alternate = ["details", "guid"]) val details: String? = null,
    // Используем Any?, чтобы GSON не падал, если придет массив вместо строки или наоборот
    @SerializedName("Category") val category: Any? = null,
    @SerializedName("CategoryDesc") val categoryDesc: Any? = null
)

data class JackettResponse(
    @SerializedName(value = "Results", alternate = ["results"]) val results: List<JackettItem>? = null
)

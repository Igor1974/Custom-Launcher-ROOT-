package com.deepnight.launcher.radio

import com.google.gson.annotations.SerializedName

data class RadioStation(
    @SerializedName("name")
    val name: String,
    
    @SerializedName("url_resolved") // Используем разрешенную ссылку, она надежнее
    val url: String,
    
    @SerializedName("stationuuid")
    val uuid: String,
    
    @SerializedName("codec")
    val codec: String = "unknown",

    @SerializedName("bitrate")
    val bitrate: Int = 0,

    @SerializedName("favicon")
    val favicon: String? = null,

    @SerializedName("tags")
    val tags: String? = ""
)

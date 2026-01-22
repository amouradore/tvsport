package com.acestream.tv.model

import com.google.gson.annotations.SerializedName

data class Match(
    @SerializedName("time") val time: String,
    @SerializedName("date") val date: String,
    @SerializedName("home_team") val homeTeam: String,
    @SerializedName("away_team") val awayTeam: String,
    @SerializedName("home_logo") val homeLogo: String = "",
    @SerializedName("away_logo") val awayLogo: String = "",
    @SerializedName("competition") val competition: String = "",
    @SerializedName("channels") val channels: List<String> = emptyList(),
    @SerializedName("link") val link: String
)

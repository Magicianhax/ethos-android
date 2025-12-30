package com.ethos.led.model

import com.google.gson.annotations.SerializedName

data class EthosResponse(
    @SerializedName("username") val username: String,
    @SerializedName("displayName") val displayName: String,
    @SerializedName("score") val score: Int
)



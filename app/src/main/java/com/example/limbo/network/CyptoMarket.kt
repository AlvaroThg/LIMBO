package com.example.limbo.network

import com.google.gson.annotations.SerializedName

data class CryptoMarketData(
    @SerializedName("ask") val ask: Float?,
    @SerializedName("bid") val bid: Float?,
    @SerializedName("time") val time: Long?
)
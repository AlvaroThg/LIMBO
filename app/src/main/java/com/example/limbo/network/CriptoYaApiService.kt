package com.example.limbo.network

import retrofit2.Call
import retrofit2.http.GET

interface CriptoYaApiService {
    @GET("usdt/bob")
    fun getUsdtBobData(): Call<Map<String, CryptoMarketData>>
}
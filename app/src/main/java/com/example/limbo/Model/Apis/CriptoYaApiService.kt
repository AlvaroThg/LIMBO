package com.example.limbo.Model.Apis

import com.example.limbo.Model.Objects.CryptoMarketData
import retrofit2.Call
import retrofit2.http.GET

interface CriptoYaApiService {
    @GET("usdt/bob")
    fun getUsdtBobData(): Call<Map<String, CryptoMarketData>>
}
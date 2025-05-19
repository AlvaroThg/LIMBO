package com.example.limbo.Model.Apis

import android.util.Log
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "https://criptoya.com/api/"
    private const val TAG = "RetrofitClient"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val apiService: CriptoYaApiService by lazy {
        try {
            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(CriptoYaApiService::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating Retrofit client: ${e.message}")
            throw e
        }
    }
}
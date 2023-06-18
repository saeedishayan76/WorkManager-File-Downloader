package com.shayan.workManager.apiService


import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

interface ApiService {

    @Streaming
    @GET("news/1402/3/25/1473538_346.mp4")
    suspend fun getVideo(): Response<ResponseBody>

    @Streaming
    @GET
    suspend fun getCustomVideo(@Url url: String): Response<ResponseBody>
}
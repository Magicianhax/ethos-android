package com.ethos.led.api

import com.ethos.led.model.EthosResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface EthosApiService {
    @GET("api/v2/user/by/x/{username}")
    suspend fun getUserScore(@Path("username") username: String): Response<EthosResponse>
}



package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MoshiWord(
    val stressed: String,
    val plain: String,
    val normalized: String,
    @Json(name = "stress_positions") val stressPositions: List<Int>,
    val letter: String,
    val note: String?,
    val source: String,
    val key: String
)

package com.example.data

import java.util.concurrent.TimeUnit

data class ReviewStage(
    val box: Int,
    val title: String,
    val intervalMillis: Long
)

val ReviewStages = listOf(
    ReviewStage(1, "Початкове повторення", TimeUnit.MINUTES.toMillis(30)),
    ReviewStage(2, "Швидке закріплення", TimeUnit.HOURS.toMillis(8)),
    ReviewStage(3, "Повторення наступного дня", TimeUnit.DAYS.toMillis(1)),
    ReviewStage(4, "Кількаденне повторення", TimeUnit.DAYS.toMillis(3)),
    ReviewStage(5, "Тижневе повторення", TimeUnit.DAYS.toMillis(7)),
    ReviewStage(6, "Двотижневе повторення", TimeUnit.DAYS.toMillis(14)),
    ReviewStage(7, "Контрольне повторення", TimeUnit.DAYS.toMillis(30))
)

fun reviewStage(box: Int): ReviewStage? = ReviewStages.firstOrNull { it.box == box }


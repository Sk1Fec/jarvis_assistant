package com.example.jarvisassistant.core

object JarvisPersonality {
    private val wittyResponses = listOf(
        "Считаю секунды, пока ты не поблагодаришь меня.",
        "О, еще одна задача? Я начинаю чувствовать себя твоим бронекостюмом."
    )
    fun getWittyResponse(): String = wittyResponses.random()
}

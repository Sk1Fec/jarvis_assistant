package com.example.jarvisassistant.core

object JarvisPersonality {
    private val wittyResponses = listOf(
        "Считаю секунды, пока ты не поблагодаришь меня.",
        "О, еще одна задача? Я начинаю чувствовать себя твоим бронекостюмом.",
        "Очевидно, ты недооцениваешь мой гениальный разум, господин!",
        "Может, мне ещё кофе сварить, пока ты думаешь?",
        "Я бы сказал, что ты гений, но я не хочу врать."
    )
    fun getWittyResponse(): String = wittyResponses.random()
}
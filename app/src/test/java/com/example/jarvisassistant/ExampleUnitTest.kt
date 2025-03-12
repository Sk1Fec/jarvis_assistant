package com.example.jarvisassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.jarvisassistant.core.CommandProcessor
import org.junit.Test
import org.junit.Assert.*

class ExampleUnitTest {

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testCalculatePercentage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val processor = CommandProcessor(context)
        val result = processor.process("сколько 25% от 100")
        assertEquals("25, я гений, да?", result)
    }

    @Test
    fun testGreeting() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val processor = CommandProcessor(context)
        val result = processor.process("привет")
        val expectedResponses = context.resources.getStringArray(R.array.greeting_responses)
        assertTrue(result in expectedResponses)
    }
}
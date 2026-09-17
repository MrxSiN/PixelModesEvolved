package my.github.MrxSiN.modeevolved.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarEventCodecTest {

    private val event = CalendarEventOption(42, "Planning", "Work", 1_800_000_000_000)

    @Test
    fun `editor input round trips`() {
        val input = CalendarEventEditorInput("work", "Work", EventFilter.OneEvent(event.id, event.title), listOf(event))
        assertEquals(input, CalendarEventCodec.decodeInput(CalendarEventCodec.encodeInput(input)))
    }

    @Test
    fun `every filter result round trips`() {
        for (filter in listOf(EventFilter.AnyEvent, EventFilter.OneEvent(42, "Planning"), EventFilter.TitleKeyword("stand-up"))) {
            val result = CalendarEventEditorResult("work", filter)
            assertEquals(result, CalendarEventCodec.decodeResult(CalendarEventCodec.encodeResult(result)))
        }
        assertNull(CalendarEventCodec.decodeResult("nonsense"))
    }

    @Test
    fun `keyword matches titles ignoring case and never reads a title it does not need`() {
        val keyword = EventFilter.TitleKeyword(" standup ")
        assertTrue(keyword.matches(1) { "Daily STANDUP" })
        assertFalse(keyword.matches(1) { "Lunch" })
        assertFalse(keyword.matches(1) { null })

        assertTrue(EventFilter.OneEvent(7, "Gym").matches(7) { error("title read") })
        assertFalse(EventFilter.OneEvent(7, "Gym").matches(8) { error("title read") })
        assertTrue(EventFilter.AnyEvent.matches(8) { error("title read") })
    }
}

package my.github.MrxSiN.modeevolved.calendar

/**
 * Which events of Android's stock calendar schedule turn a Mode on.
 *
 * The stock controls already narrow events by calendar and invite response;
 * a filter narrows what is left.
 */
sealed interface EventFilter {

    /**
     * @param title reads the event's title, only when this filter needs it,
     * because in system_server that costs a calendar query.
     */
    fun matches(eventId: Long, title: () -> String?): Boolean

    /** Android's own behavior: every event counts. */
    data object AnyEvent : EventFilter {
        override fun matches(eventId: Long, title: () -> String?): Boolean = true
    }

    /** One event, and every instance of it when it repeats. [title] is kept for display. */
    data class OneEvent(val id: Long, val title: String) : EventFilter {
        override fun matches(eventId: Long, title: () -> String?): Boolean = eventId == id
    }

    /** Every event whose title contains [keyword], ignoring case, including events created later. */
    data class TitleKeyword(val keyword: String) : EventFilter {
        override fun matches(eventId: Long, title: () -> String?): Boolean =
            title()?.contains(keyword.trim(), ignoreCase = true) == true
    }
}

package com.hopcape.odo

import app.cash.sqldelight.db.SqlDriver
import org.koin.core.context.GlobalContext

/**
 * The curated ideas, written straight into the local table.
 *
 * The catalogue is pulled from the server and its policy needs a session, so a test that is
 * not signed in reads `200 []` — the anon fallback — and the section is left out. Seeding
 * locally is what lets the list be photographed at all, and it is the same trick the bill
 * check's own robot uses for a service log.
 */
internal fun seedFeatureIdeas() {
    val driver: SqlDriver = GlobalContext.get().get()
    IDEAS.forEachIndexed { index, (title, status) ->
        driver.execute(
            identifier = null,
            sql = """
                INSERT OR REPLACE INTO feature_ideas
                    (id, title, status, votes, created_at, updated_at, deleted_at)
                VALUES (?, ?, ?, ?, ?, ?, NULL)
            """.trimIndent(),
            parameters = 7,
        ) {
            bindString(0, "idea-$index")
            bindString(1, title)
            bindString(2, status)
            bindLong(3, VOTES[index])
            bindString(4, SEEDED_AT)
            bindString(5, SEEDED_AT)
        }
    }
}

/** The four the mockup names, so the capture is the screen as designed. */
private val IDEAS = listOf(
    "Two cars on one account" to "IN_PROGRESS",
    "Export costs to Excel" to "UNDER_REVIEW",
    "Hindi interface" to "UNDER_REVIEW",
    "Insurance renewal reminders" to "SHIPPING",
)

/** Counts wide enough to prove the pill does not squeeze the title beside it. */
private val VOTES = listOf(412L, 288L, 231L, 195L)

private const val SEEDED_AT = "2026-09-05T10:00:00Z"

package com.hopcape.odo.web.blog.data

import com.hopcape.odo.web.blog.domain.model.Analytics
import com.hopcape.odo.web.blog.domain.model.ArticleBlock
import com.hopcape.odo.web.blog.domain.model.Author
import com.hopcape.odo.web.blog.domain.model.Category
import com.hopcape.odo.web.blog.domain.model.MediaItem
import com.hopcape.odo.web.blog.domain.model.PostStatus
import com.hopcape.odo.web.blog.domain.model.PostSummary
import com.hopcape.odo.web.blog.domain.model.Session
import com.hopcape.odo.web.blog.domain.model.TextRun
import com.hopcape.odo.web.blog.domain.model.TopPost
import kotlinx.datetime.LocalDate

/**
 * The corpus the sample repositories serve.
 *
 * These are the design's own posts, at the lengths the frames were drawn at,
 * translated into English along with the rest of the interface. A screen built
 * against invented lorem looks finished until real copy lands in it and every
 * line wraps somewhere else.
 *
 * What an author actually publishes is their choice and arrives as data — none
 * of it comes from here.
 *
 * It is also the only file that has to disappear when Supabase arrives — nothing
 * above the repositories imports it, so deleting it takes the sample data out and
 * leaves the screens alone.
 *
 * The numbers here are made up. They are here so a table has rows and a chart has
 * a shape, and none of them may ever be quoted anywhere a reader can see.
 */
internal object SampleContent {

    // ── Categories ───────────────────────────────────────────────────────────
    // The blurb is the topic only. Counts are appended by the UI from the posts
    // actually filed here, so a category can never advertise articles that were
    // never written.

    val challans = Category("challans", "Challans", "E-challans, court cases and payment")
    val serviceCosts = Category("service-costs", "Service costs", "Rates taken from real bills")
    val documents = Category("documents", "Documents", "RC, insurance, PUC and renewals")
    val resale = Category("resale", "Resale", "Before you sell, and while you are selling")
    val fuel = Category("fuel", "Fuel", "Mileage, running cost and fuel logging")

    val categories: List<Category> = listOf(challans, serviceCosts, documents, resale, fuel)

    // ── Authors ──────────────────────────────────────────────────────────────

    val rahul = Author(
        slug = "rahul-deshmukh",
        name = "Rahul Deshmukh",
        initial = "R",
        bio = "Co-founder of Odo. I drive a 2020 Swift in Pune and have scanned every bill " +
            "for the last four years — which is where this app came from.",
        articleCount = 9,
        topics = "Challans · Costs · Resale",
        since = "March 2025",
    )

    // ── Posts ────────────────────────────────────────────────────────────────
    // Newest first. The repositories do not sort; this order is the order.

    val posts: List<PostSummary> = listOf(
        PostSummary(
            slug = "how-to-check-challans",
            title = "How to check your challans — the full guide",
            dek = "Parivahan, state portals and apps — every route, and what each one misses.",
            category = challans,
            publishedOn = LocalDate(2026, 8, 18),
            readingMinutes = 8,
        ),
        PostSummary(
            slug = "new-tyre-prices",
            title = "What new tyres should actually cost",
            dek = "By brand, size and city — from real bills.",
            category = serviceCosts,
            publishedOn = LocalDate(2026, 8, 14),
            readingMinutes = 5,
        ),
        PostSummary(
            slug = "brake-pad-prices",
            title = "What should brake pads cost?",
            dek = "Real rates from Pune, Delhi and Mumbai — from 240 verified bills.",
            category = serviceCosts,
            publishedOn = LocalDate(2026, 8, 11),
            readingMinutes = 5,
        ),
        PostSummary(
            slug = "challans-that-go-to-court",
            title = "When a challan goes to court",
            dek = "What changes after 90 days, and what you have to do about it.",
            category = challans,
            publishedOn = LocalDate(2026, 8, 9),
            readingMinutes = 5,
        ),
        PostSummary(
            slug = "expired-puc",
            title = "Your PUC expired — now what?",
            dek = "What the fine is, how to renew, and how long it takes.",
            category = documents,
            publishedOn = LocalDate(2026, 8, 4),
            readingMinutes = 4,
        ),
        PostSummary(
            slug = "disputing-a-wrong-challan",
            title = "A challan that is not yours — how to dispute it",
            dek = "From a number-plate mismatch to a wrong location.",
            category = challans,
            publishedOn = LocalDate(2026, 8, 2),
            readingMinutes = 6,
        ),
        PostSummary(
            slug = "five-things-before-selling",
            title = "Five things to do before you sell",
            dek = "Small jobs that look large to a buyer.",
            category = resale,
            publishedOn = LocalDate(2026, 7, 28),
            readingMinutes = 6,
        ),
        PostSummary(
            slug = "losing-mileage",
            title = "Losing mileage?",
            dek = "The most common reason is tyre pressure — and that one is free.",
            category = fuel,
            publishedOn = LocalDate(2026, 7, 21),
            readingMinutes = 4,
        ),
        PostSummary(
            slug = "when-to-get-a-wheel-alignment",
            title = "When to get a wheel alignment",
            dek = "The signs that say it is time.",
            category = serviceCosts,
            publishedOn = LocalDate(2026, 7, 7),
            readingMinutes = 4,
        ),
    )

    /** Views, for the CMS table and the analytics page. Only some posts have any. */
    val views: Map<String, Int> = mapOf(
        "how-to-check-challans" to 14_208,
        "brake-pad-prices" to 6_431,
        "expired-puc" to 3_902,
        "five-things-before-selling" to 2_588,
        "challans-that-go-to-court" to 1_744,
        "new-tyre-prices" to 1_120,
        "disputing-a-wrong-challan" to 903,
        "losing-mileage" to 761,
        "when-to-get-a-wheel-alignment" to 405,
    )

    /** "2 days ago" style labels. Text because a fake clock would be worse. */
    val updatedLabels: Map<String, String> = mapOf(
        "how-to-check-challans" to "2 days ago",
        "new-tyre-prices" to "6 days ago",
        "brake-pad-prices" to "9 days ago",
        "challans-that-go-to-court" to "11 days ago",
        "expired-puc" to "16 days ago",
        "disputing-a-wrong-challan" to "18 days ago",
        "five-things-before-selling" to "23 days ago",
        "losing-mileage" to "1 month ago",
        "when-to-get-a-wheel-alignment" to "1 month ago",
    )

    // ── Bodies ───────────────────────────────────────────────────────────────

    /**
     * The one article written out in full.
     *
     * The design draws this post at every size, so it is the one that has to have
     * every block type in it: a lede, sections, a bolded run, the callout, and the
     * app shown after the answer is already complete.
     */
    private val challanGuide: List<ArticleBlock> = listOf(
        ArticleBlock.Paragraph(
            plain(
                "Most people never find out that there is a challan in their name. A challan issued " +
                    "by a camera does not arrive at your door — it goes straight into a database, and " +
                    "you hear about it when you go to renew your insurance or sell the car.",
            ),
        ),
        ArticleBlock.Section("what-a-challan-is", "What a challan is"),
        ArticleBlock.Paragraph(
            listOf(
                TextRun("There are two kinds. "),
                TextRun("On the spot", bold = true),
                TextRun(" — the traffic police stopped you and handed over a receipt. And an "),
                TextRun("e-challan", bold = true),
                TextRun(" — a camera caught it, nobody stopped you, and the notice only exists in the system."),
            ),
        ),
        ArticleBlock.Callout(
            label = "WORTH KNOWING",
            runs = plain(
                "An e-challan left unpaid for 90 days goes to court in several states. After that it " +
                    "cannot be paid online — you have to appear in person.",
            ),
        ),
        ArticleBlock.Section("checking-on-parivahan", "Checking on Parivahan"),
        ArticleBlock.Paragraph(
            plain(
                "Open echallan.parivahan.gov.in, enter the registration number and the last five digits " +
                    "of the chassis number, then verify the OTP. This is the central database — but not " +
                    "every state feeds it at the same speed.",
            ),
        ),
        ArticleBlock.Section("checking-on-a-state-portal", "Checking on a state portal"),
        ArticleBlock.Paragraph(
            plain(
                "Every state runs its own — delhitrafficpolice.nic.in for Delhi, " +
                    "mahatrafficechallan.gov.in for Maharashtra. These are usually faster than the central " +
                    "database, but they only show challans from that one state. Drive in another state and " +
                    "what you picked up there will not appear here.",
            ),
        ),
        ArticleBlock.Section("checking-in-odo", "Checking in Odo — one place"),
        ArticleBlock.AppShowcase(
            heading = "Odo has a screen for this",
            body = "Enter the number and Odo checks every source at once, shows the whole list, and tells " +
                "you how old the data behind it is.",
            callToAction = "Download Odo",
        ),
        ArticleBlock.Section("challans-in-court", "Challans that reach court"),
        ArticleBlock.Paragraph(
            plain(
                "After 90 days several states move a challan to a virtual court. Parivahan then shows it " +
                    "as \"disposed\" or \"court\", which means online payment is closed. You have to go " +
                    "through the e-court portal instead, and sometimes attend a hearing. The amount usually " +
                    "goes up too.",
            ),
        ),
        ArticleBlock.Section("how-long-you-have", "How long you have"),
        ArticleBlock.Paragraph(
            plain(
                "As little time as you can take. Several states add a late fee after 60 days, and at 90 " +
                    "days it becomes a court reference — at which point the amount rises and the process " +
                    "gets long.",
            ),
        ),
    )

    /**
     * Bodies for the rest.
     *
     * Short on purpose. They exist so every card on the index leads somewhere that
     * renders, not so the sample corpus becomes a writing project.
     */
    val bodies: Map<String, List<ArticleBlock>> = buildMap {
        put("how-to-check-challans", challanGuide)
        posts.filter { it.slug != "how-to-check-challans" }.forEach { post ->
            put(
                post.slug,
                listOf(
                    ArticleBlock.Paragraph(plain(post.dek)),
                    ArticleBlock.Section("in-short", "In short"),
                    ArticleBlock.Paragraph(
                        plain(
                            "This one is still being written. Until it is, the line above is the answer.",
                        ),
                    ),
                ),
            )
        }
    }

    // ── Admin ────────────────────────────────────────────────────────────────

    val session = Session(authorSlug = rahul.slug, name = "Rahul", initial = "R")

    /** The address the sample sign-in accepts. Any other is rejected. */
    const val SIGN_IN_EMAIL: String = "rahul@odo.app"

    /** The password it accepts. Sample data — nothing here guards anything. */
    const val SIGN_IN_PASSWORD: String = "odo"

    /** Three unpublished posts, so the drafts filter has something in it. */
    val drafts: List<Triple<String, String, String>> = listOf(
        Triple("draft-insurance-resale", "Resale value after an insurance claim", "Today"),
        Triple("draft-service-interval", "Service intervals — is the manufacturer's number right?", "3 days ago"),
        Triple("draft-rto-transfer", "RTO transfer — which papers you need", "1 week ago"),
    )

    val media: List<MediaItem> = listOf(
        MediaItem("challan-list.png", "/blog/assets/sample/challan-list.png", "Challan list in Odo — 2 pending, Rs. 1,500"),
        MediaItem("lookup.png", "/blog/assets/sample/lookup.png"),
        MediaItem("clean-state.png", "/blog/assets/sample/clean-state.png"),
        MediaItem("costs.png", "/blog/assets/sample/costs.png"),
    )

    val analytics = Analytics(
        windowLabel = "Last 30 days",
        views = 31_402,
        searchSharePercent = 78,
        appInstalls = 1_146,
        topPosts = listOf(
            TopPost("How to check your challans", 14_208),
            TopPost("What should brake pads cost?", 6_431),
            TopPost("Your PUC expired", 3_902),
            TopPost("Five things to do before you sell", 2_588),
        ),
    )

    val publishedStatus: PostStatus = PostStatus.PUBLISHED

    /** A paragraph with no emphasis in it, which is most of them. */
    private fun plain(text: String): List<TextRun> = listOf(TextRun(text))
}

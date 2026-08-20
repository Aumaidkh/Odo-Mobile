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
 * Every word of it is the design's own copy, and that is the point: a screen
 * built against invented lorem looks finished until real Hinglish lands in it
 * and every line wraps somewhere else. These are the posts the frames were drawn
 * with, at the lengths they were drawn at.
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

    val challans = Category("challans", "Challans", "E-challan, court cases aur payment")
    val serviceCosts = Category("service-costs", "Service costs", "Asli bills se nikale gaye rates")
    val documents = Category("documents", "Documents", "RC, insurance, PUC aur renewals")
    val resale = Category("resale", "Resale", "Bechne se pehle aur bechte waqt")
    val fuel = Category("fuel", "Fuel", "Mileage, running cost aur fuel logging")

    val categories: List<Category> = listOf(challans, serviceCosts, documents, resale, fuel)

    // ── Authors ──────────────────────────────────────────────────────────────

    val rahul = Author(
        slug = "rahul-deshmukh",
        name = "Rahul Deshmukh",
        initial = "R",
        bio = "Odo ka co-founder. Pune me ek 2020 Swift chalata hoon, aur pichle chaar saal se " +
            "har bill scan karta hoon — yehi se ye app bani.",
        articleCount = 9,
        topics = "Challans · Costs · Resale",
        since = "March 2025",
    )

    // ── Posts ────────────────────────────────────────────────────────────────
    // Newest first. The repositories do not sort; this order is the order.

    val posts: List<PostSummary> = listOf(
        PostSummary(
            slug = "challan-kaise-check-karein",
            title = "Challan kaise check karein — poori guide",
            dek = "Parivahan, state portals aur apps — sab tarike, aur har ek me kya galat ho sakta hai.",
            category = challans,
            publishedOn = LocalDate(2026, 8, 18),
            readingMinutes = 8,
        ),
        PostSummary(
            slug = "naye-tyres-ka-price",
            title = "Naye tyres ka sahi price kya hai",
            dek = "Brand, size aur city ke hisaab se — asli bills se.",
            category = serviceCosts,
            publishedOn = LocalDate(2026, 8, 14),
            readingMinutes = 5,
        ),
        PostSummary(
            slug = "brake-pads-price",
            title = "Brake pads ka sahi price kya hai?",
            dek = "Pune, Delhi aur Mumbai ke asli rates — 240 verified bills se.",
            category = serviceCosts,
            publishedOn = LocalDate(2026, 8, 11),
            readingMinutes = 5,
        ),
        PostSummary(
            slug = "court-case-wala-challan",
            title = "Court case wala challan kya hota hai",
            dek = "90 din ke baad kya badalta hai, aur kya karna padta hai.",
            category = challans,
            publishedOn = LocalDate(2026, 8, 9),
            readingMinutes = 5,
        ),
        PostSummary(
            slug = "puc-expire",
            title = "PUC expire ho gaya — ab kya?",
            dek = "Fine kitna, renew kaise, aur kitna time lagta hai.",
            category = documents,
            publishedOn = LocalDate(2026, 8, 4),
            readingMinutes = 4,
        ),
        PostSummary(
            slug = "galat-challan-dispute",
            title = "Galat challan aa gaya — dispute kaise karein",
            dek = "Number plate mismatch se lekar wrong location tak.",
            category = challans,
            publishedOn = LocalDate(2026, 8, 2),
            readingMinutes = 6,
        ),
        PostSummary(
            slug = "bechne-se-pehle-5-cheezein",
            title = "Bechne se pehle ye 5 cheezein",
            dek = "Chhote kaam jo buyer ke saamne bade dikhte hain.",
            category = resale,
            publishedOn = LocalDate(2026, 7, 28),
            readingMinutes = 6,
        ),
        PostSummary(
            slug = "mileage-kam-ho-rahi-hai",
            title = "Mileage kam ho rahi hai?",
            dek = "Sabse aam wajah tyre pressure hoti hai — aur woh free hai.",
            category = fuel,
            publishedOn = LocalDate(2026, 7, 21),
            readingMinutes = 4,
        ),
        PostSummary(
            slug = "wheel-alignment-kab",
            title = "Wheel alignment kab karwana chahiye",
            dek = "Signs jo batate hain ki ab time aa gaya.",
            category = serviceCosts,
            publishedOn = LocalDate(2026, 7, 7),
            readingMinutes = 4,
        ),
    )

    /** Views, for the CMS table and the analytics page. Only some posts have any. */
    val views: Map<String, Int> = mapOf(
        "challan-kaise-check-karein" to 14_208,
        "brake-pads-price" to 6_431,
        "puc-expire" to 3_902,
        "bechne-se-pehle-5-cheezein" to 2_588,
        "court-case-wala-challan" to 1_744,
        "naye-tyres-ka-price" to 1_120,
        "galat-challan-dispute" to 903,
        "mileage-kam-ho-rahi-hai" to 761,
        "wheel-alignment-kab" to 405,
    )

    /** "2 days ago" style labels. Text because a fake clock would be worse. */
    val updatedLabels: Map<String, String> = mapOf(
        "challan-kaise-check-karein" to "2 days ago",
        "naye-tyres-ka-price" to "6 days ago",
        "brake-pads-price" to "9 days ago",
        "court-case-wala-challan" to "11 days ago",
        "puc-expire" to "16 days ago",
        "galat-challan-dispute" to "18 days ago",
        "bechne-se-pehle-5-cheezein" to "23 days ago",
        "mileage-kam-ho-rahi-hai" to "1 month ago",
        "wheel-alignment-kab" to "1 month ago",
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
                "Zyadatar log ko pata hi nahi chalta ki unke naam pe challan hai. Camera se kata hua " +
                    "challan aapke ghar nahi aata — woh seedha database me chala jaata hai, aur aapko tab " +
                    "pata chalta hai jab insurance renew karwane jaate ho ya gaadi bechne.",
            ),
        ),
        ArticleBlock.Section("challan-hota-kya-hai", "Challan hota kya hai"),
        ArticleBlock.Paragraph(
            listOf(
                TextRun("Do tarah ke hote hain. "),
                TextRun("On-the-spot", bold = true),
                TextRun(" — traffic police ne roka, receipt di. Aur "),
                TextRun("e-challan", bold = true),
                TextRun(" — camera ne pakda, koi nahi roka, notice bas system me hai."),
            ),
        ),
        ArticleBlock.Callout(
            label = "DHYAN DEIN",
            runs = plain(
                "E-challan 90 din tak na bhara jaaye toh kai states me woh court me chala jaata hai. " +
                    "Phir online nahi bharta — court jaana padta hai.",
            ),
        ),
        ArticleBlock.Section("parivahan-se-check-karna", "Parivahan se check karna"),
        ArticleBlock.Paragraph(
            plain(
                "echallan.parivahan.gov.in kholein, gaadi ka number aur chassis ke aakhri 5 digit daalein, " +
                    "OTP verify karein. Ye central database hai — par har state ka data isme same speed se " +
                    "nahi aata.",
            ),
        ),
        ArticleBlock.Section("state-portal-se", "State portal se"),
        ArticleBlock.Paragraph(
            plain(
                "Har state ka apna portal hai — Delhi ke liye delhitrafficpolice.nic.in, Maharashtra ke " +
                    "liye mahatrafficechallan.gov.in. Ye aksar central database se tez hote hain, lekin sirf " +
                    "usi state ke challan dikhate hain. Agar aapne doosre state me gaadi chalayi hai, wahan " +
                    "ka challan yahan nahi milega.",
            ),
        ),
        ArticleBlock.Section("odo-se-ek-jagah", "Odo se — ek jagah"),
        ArticleBlock.AppShowcase(
            heading = "Odo me ye ek screen hai",
            body = "Number daalein — Odo saare sources ek saath check karta hai aur poori list dikhata " +
                "hai, saath me ye bhi ki data kitna purana hai.",
            callToAction = "Odo download karein",
        ),
        ArticleBlock.Section("court-case-wale-challan", "Court case wale challan"),
        ArticleBlock.Paragraph(
            plain(
                "90 din ke baad kai states challan ko virtual court me bhej dete hain. Tab woh Parivahan " +
                    "pe \"disposed\" ya \"court\" dikhta hai — matlab online payment band. Aapko e-court " +
                    "portal pe jaana padta hai, aur kabhi-kabhi sunwai pe bhi. Amount bhi usually badh " +
                    "jaati hai.",
            ),
        ),
        ArticleBlock.Section("kitne-din-me-bharna-hai", "Kitne din me bharna hai"),
        ArticleBlock.Paragraph(
            plain(
                "Jitni jaldi ho sake. 60 din ke baad kai states late fee lagate hain, aur 90 din pe woh " +
                    "court reference ban jaata hai — tab amount badh jaati hai aur process lamba ho jaata hai.",
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
        put("challan-kaise-check-karein", challanGuide)
        posts.filter { it.slug != "challan-kaise-check-karein" }.forEach { post ->
            put(
                post.slug,
                listOf(
                    ArticleBlock.Paragraph(plain(post.dek)),
                    ArticleBlock.Section("shuruaat", "Shuruaat"),
                    ArticleBlock.Paragraph(
                        plain(
                            "Ye post abhi likhi ja rahi hai. Jab tak, upar wali line hi iska jawab hai.",
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
        Triple("draft-insurance-resale", "Insurance claim ke baad resale value", "Today"),
        Triple("draft-service-interval", "Service interval — company ka number sahi hai?", "3 days ago"),
        Triple("draft-rto-transfer", "RTO transfer — kaunse papers lagte hain", "1 week ago"),
    )

    val media: List<MediaItem> = listOf(
        MediaItem("challan-list.png", "/blog/assets/sample/challan-list.png", "Odo me challan list — 2 pending, Rs. 1,500"),
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
            TopPost("Challan kaise check karein", 14_208),
            TopPost("Brake pads ka sahi price", 6_431),
            TopPost("PUC expire ho gaya", 3_902),
            TopPost("Bechne se pehle ye 5 cheezein", 2_588),
        ),
    )

    val publishedStatus: PostStatus = PostStatus.PUBLISHED

    /** A paragraph with no emphasis in it, which is most of them. */
    private fun plain(text: String): List<TextRun> = listOf(TextRun(text))
}

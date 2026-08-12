package com.hopcape.odo

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import android.app.Activity
import android.app.Instrumentation.ActivityResult
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.db.SqlDriver
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
import com.hopcape.odo.feature.documentvault.presentation.DocumentVaultTestTags
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import java.io.File
import java.time.LocalDate

/**
 * The words the document vault puts on screen, mirrored from its `strings.xml`.
 *
 * Copied rather than read, for the same reason as [LogCopy]: Compose Resources keeps a
 * feature's generated `Res` internal to its own module, so `:androidApp` cannot reach it.
 * Asserting on the copy an owner actually reads is the point.
 *
 * Typographic apostrophes (’) come straight from the resource files; an ASCII quote would
 * never match.
 */
internal object VaultCopy {
    const val TITLE = "Documents"

    const val HEADER_ADD_TITLE = "Add your documents"
    const val HEADER_COVERED_TITLE = "You’re fully covered"

    const val DOC_INSURANCE = "Insurance"
    const val DOC_PUC = "Pollution (PUC)"
    const val DOC_RC = "Registration (RC)"
    const val DOC_LICENCE = "Driving licence"

    const val STATUS_NOT_ADDED = "Not added yet"
    const val STATUS_LIFETIME = "Lifetime · on file"

    const val PILL_NOT_ADDED = "Not added"
    const val PILL_VALID = "Valid"
    const val PILL_EXPIRES_SOON = "Expires soon"
    const val PILL_EXPIRED = "Expired"

    const val ADD_DOCUMENT_BAR = "Add a document"

    /* Add screen. */
    const val ADD_TITLE = "Add document"
    const val ADD_CHIP_INSURANCE = "Insurance"
    const val ADD_CHIP_PUC = "PUC"
    const val ADD_CHIP_RC = "RC"
    const val CAPTURE_SCAN = "Scan with camera"
    const val CAPTURE_UPLOAD = "Upload a file"
    const val CAPTURE_DIGILOCKER = "Import from DigiLocker"
    const val CAPTURE_UNAVAILABLE = "This way of adding a document is coming soon. Upload a file for now."
    const val WRITE_FAILED = "Something went wrong. Please try again."
    /**
     * FeatureFlags.PAYWALL_ENABLED: goes back to "Your free plan stores 3 documents. Delete
     * one to add another." when the flag goes true.
     */
    const val LIMIT_REACHED = "Odo stores up to 3 documents. Delete one to add another."

    /* Confirm step — owned by the scanner, reached by both ways of adding a document. */
    const val REVIEW_TITLE = "Check the document"
    const val REVIEW_SAVE = "Save to vault"
    const val REVIEW_EXPIRY_REQUIRED = "Add the expiry date so Odo can remind you."

    /* Success screen. */
    const val SUCCESS_NO_REMINDER = "Safely stored in your vault."
    const val SUCCESS_BACK = "Back to Documents"
    const val SUCCESS_ADD_ANOTHER = "Add another"

    /* Detail screen. */
    const val DETAIL_VERIFIED = "Verified"
    const val DETAIL_VIEW = "View"
    const val DETAIL_LIFETIME = "Never expires"
    const val DETAIL_FILE_MISSING =
        "The stored file is missing. Replace it to view or share this document."
    const val DETAIL_RENEW = "Renew now"

    const val MENU_LABEL = "More"
    const val CLOSE_LABEL = "Close"
    const val BACK_LABEL = "Back"
    const val MENU_EDIT_DATES = "Edit dates"
    const val MENU_REPLACE = "Replace file"
    const val MENU_SHARE = "Share"
    const val MENU_DOWNLOAD = "Download PDF"
    const val MENU_DELETE = "Delete document"

    /* Edit dates sheet. */
    const val DATES_TITLE = "Edit dates"
    const val DATES_EXPIRY = "Expires on"
    const val DATES_NOT_SET = "Not set"
    const val DATES_REQUIRED = "Add the expiry date so Odo can remind you."
    const val DATES_SAVE = "Save dates"

    /* Share sheet. */
    const val SHARE_WHATSAPP = "WhatsApp"
    const val SHARE_EMAIL = "Email"
    const val SHARE_COPY = "Copy link"

    /** The garage is the only route into the vault today, so its copy is on the path. */
    const val GARAGE_TAB = "Garage"
    const val GARAGE_MANAGE = "Manage"

    /** `"%1$s added"` — the success screen's heading. */
    fun added(document: String) = "$document added"

    /** `"All %1$d documents are valid and safely on file."` */
    fun coveredBody(count: Int) = "All $count documents are valid and safely on file."

    /** `"%1$d document needs attention"` / `"%1$d documents need attention"`. */
    fun attentionTitle(count: Int) =
        if (count == 1) "$count document needs attention" else "$count documents need attention"

    /** `"Renew your %1$s before it lapses to avoid a fine."` — the name arrives lowercased. */
    fun attentionBody(document: String) = "Renew your ${document.lowercase()} before it lapses to avoid a fine."

    fun validTill(date: LocalDate) = "Valid till ${formatted(date)}"
    fun expiresIn(days: Int, date: LocalDate) = "Expires in $days days · ${formatted(date)}"
    fun expiredOn(date: LocalDate) = "Expired · ${formatted(date)}"
    fun reminder(days: Int) = "We’ll remind you $days days before it expires"
    fun detailExpiresIn(days: Int) = "Expires in $days days"
    fun issuedOn(date: LocalDate) = "Issued ${formatted(date)}"
    /** `"Share %1$s"` — the sheet lowercases the name it is handed. */
    fun shareTitle(document: String) = "Share ${document.lowercase()}"

    /** The app's own date format ("3 Jul 2027"), mirrored from `core.domain.shared.formatDate`. */
    private fun formatted(date: LocalDate) = "${date.dayOfMonth} ${MONTHS[date.monthValue - 1]} ${date.year}"

    private val MONTHS = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )
}

/**
 * The documents each test seeds, and the days they are dated against.
 *
 * Expiry dates are **relative to today**, not fixed: the vault resolves validity against the
 * device clock, so a hardcoded date would drift into a different status and start failing on
 * its own. Seeding "seven days from now" is what keeps "Expires soon" meaning that.
 */
internal object VaultFixtures {
    const val INSURANCE_ID = "doc-insurance"
    const val PUC_ID = "doc-puc"
    const val RC_ID = "doc-rc"
    const val LICENCE_ID = "doc-licence"

    const val INSURANCE_TITLE = "SafeDrive comprehensive"

    /** Comfortably outside the 30-day renewal window. */
    val INSURANCE_EXPIRY: LocalDate get() = LocalDate.now().plusDays(200)

    /** Inside the window, so it is the document that needs attention. */
    const val PUC_DAYS_LEFT = 7
    val PUC_EXPIRY: LocalDate get() = LocalDate.now().plusDays(PUC_DAYS_LEFT.toLong())

    /** PUC leads are 15 and 3 days; the 15-day one has passed, so 3 is what is left. */
    const val PUC_REMINDER_DAYS = 3

    val LICENCE_EXPIRY: LocalDate get() = LocalDate.now().plusDays(900)

    /** Already lapsed. */
    const val EXPIRED_DAYS_AGO = 5
    val EXPIRED_ON: LocalDate get() = LocalDate.now().minusDays(EXPIRED_DAYS_AGO.toLong())

    val ISSUED_ON: LocalDate get() = LocalDate.now().minusDays(160)

    /** The file the stubbed picker hands back. */
    const val PICKED_FILE = "policy.pdf"
}

private typealias VaultTestRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/* ------------------------------ Database seeding ------------------------------ */

private fun vaultDriver(): SqlDriver = GlobalContext.get().get()

/**
 * Empty the owner's tables, documents included, and remove the files behind them.
 *
 * [resetOwnerData] covers the profile, the car and the service log; documents are this
 * feature's table and its files live outside the database, so both go here.
 */
internal fun resetVault() {
    resetOwnerData()
    vaultDriver().execute(null, "DELETE FROM documents", 0)
    announceDocumentWrites()
    File(appContext().filesDir, "documents").deleteRecursively()
}

/**
 * Store one document, and by default the file behind it.
 *
 * Written straight to the database because the add flow can only produce today's shape: an
 * uploaded file with no expiry date and no issue date. Every status the vault renders —
 * valid, expiring, lapsed — needs an expiry the UI has no field for yet, and the Verified
 * badge needs a DigiLocker copy there is no importer for.
 *
 * [withFile] left false seeds a row whose file is gone, which is what a restore from backup
 * leaves behind.
 */
internal fun seedDocument(
    id: String,
    type: DocumentType,
    expiresOn: LocalDate? = null,
    source: String = "UPLOADED",
    title: String? = null,
    issuedOn: LocalDate? = null,
    withFile: Boolean = true,
) {
    val storagePath = "documents/${LogFixtures.CAR}/$id.pdf"
    vaultDriver().execute(
        null,
        "INSERT INTO documents (id, car_id, owner_id, doc_type, title, storage_path, doc_source, " +
            "issued_date, expiry_date, created_at, updated_at, sync_status) VALUES " +
            "('$id', '${LogFixtures.CAR}', '${LogFixtures.OWNER}', '${type.name}', ${title.sqlText()}, " +
            "'$storagePath', '$source', ${issuedOn?.toString().sqlText()}, ${expiresOn?.toString().sqlText()}, " +
            "'$SEEDED_DOCUMENT_AT', '$SEEDED_DOCUMENT_AT', 'PENDING')",
        0,
    )
    if (withFile) writeStoredFile(storagePath)
    announceDocumentWrites()
}

/** The four papers the vault tracks, each in a different state. */
internal fun seedTrackedDocuments() {
    seedDocument(
        id = VaultFixtures.INSURANCE_ID,
        type = DocumentType.INSURANCE,
        expiresOn = VaultFixtures.INSURANCE_EXPIRY,
        source = "DIGILOCKER",
        title = VaultFixtures.INSURANCE_TITLE,
        issuedOn = VaultFixtures.ISSUED_ON,
    )
    seedDocument(id = VaultFixtures.PUC_ID, type = DocumentType.PUC, expiresOn = VaultFixtures.PUC_EXPIRY)
    seedDocument(id = VaultFixtures.RC_ID, type = DocumentType.RC)
}

/** Every tracked paper on file and in date — the "fully covered" header's precondition. */
internal fun seedFullyCoveredVault() {
    seedDocument(
        id = VaultFixtures.INSURANCE_ID,
        type = DocumentType.INSURANCE,
        expiresOn = VaultFixtures.INSURANCE_EXPIRY,
    )
    seedDocument(
        id = VaultFixtures.PUC_ID,
        type = DocumentType.PUC,
        expiresOn = LocalDate.now().plusDays(120),
    )
    seedDocument(id = VaultFixtures.RC_ID, type = DocumentType.RC)
    seedDocument(
        id = VaultFixtures.LICENCE_ID,
        type = DocumentType.LICENCE,
        expiresOn = VaultFixtures.LICENCE_EXPIRY,
    )
}

/**
 * Soft-delete a document through the repository, standing in for a delete made on another
 * surface (or arriving on a sync pull).
 *
 * Through the repository rather than as raw SQL, and that distinction is the point:
 * SQLDelight only re-runs a query when the write went through SQLDelight, so a hand-written
 * UPDATE would change the row without any open screen hearing about it.
 */
internal fun softDeleteDocument(id: String) = runBlocking {
    GlobalContext.get().get<DocumentRepository>().softDelete(DocumentId(id))
}

/**
 * Tell SQLDelight that `documents` changed — the seeds are hand-written SQL, which goes in
 * behind its back, so an already-collecting screen would otherwise keep serving what it read
 * before the seed. Same reason as the service log's `announceSeededWrites`.
 */
private fun announceDocumentWrites() = vaultDriver().notifyListeners("documents")

/** SQL literal or NULL — these seeds are hand-written SQL, so nullability is explicit. */
private fun String?.sqlText(): String = this?.let { "'${it.replace("'", "''")}'" } ?: "NULL"

/** A fixed timestamp for every seeded row; nothing under test reads it. */
private const val SEEDED_DOCUMENT_AT = "2026-07-01T00:00:00Z"

/* ------------------------------ Files ------------------------------ */

private fun appContext() = InstrumentationRegistry.getInstrumentation().targetContext

/** The bytes a seeded row points at, in the same private directory the app writes to. */
private fun writeStoredFile(storagePath: String) {
    val file = File(appContext().filesDir, storagePath)
    file.parentFile?.mkdirs()
    file.writeBytes(PDF_BYTES)
}

/**
 * Whether the vault's storage directory holds no files.
 *
 * Asked by id would be better, but the id of a document added through the UI is minted by the
 * app; "the directory is empty" is the same assertion for a vault with one document in it.
 */
internal fun noVaultFilesRemain(): Boolean =
    File(appContext().filesDir, "documents").walkTopDown().none { it.isFile }

/**
 * Answer the system document picker with a file, instead of opening it.
 *
 * The picker is another app's activity: an instrumented test can neither drive it nor rely on
 * what it shows. Stubbing the result is the only way to test what the vault does *with* a
 * picked file, which is the part Odo owns.
 */
internal fun stubPickedFile(): Uri {
    val file = File(appContext().cacheDir, VaultFixtures.PICKED_FILE)
    file.writeBytes(PDF_BYTES)
    val uri = Uri.fromFile(file)
    intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
        .respondWith(ActivityResult(Activity.RESULT_OK, Intent().setData(uri)))
    return uri
}

/** Enough of a PDF for the content resolver to name its type; nothing reads the contents. */
private val PDF_BYTES = "%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n"
    .toByteArray()

/* ------------------------------ Navigation ------------------------------ */

/** The first frame waits on the start-destination read, and on a cold start on the seed. */
private const val VAULT_START_UP_TIMEOUT_MILLIS = 20_000L

/**
 * Walk from Home to the vault the way an owner reaches it: the Garage tab, then "Manage"
 * beside its documents.
 *
 * Driving that path is part of what these tests prove — the garage hands the vault nothing,
 * so the vault has to name the car itself through the active-car port.
 */
internal fun VaultTestRule.openVault() {
    awaitText(VaultCopy.GARAGE_TAB, VAULT_START_UP_TIMEOUT_MILLIS)
    onNodeWithText(VaultCopy.GARAGE_TAB).performClick()
    awaitText(VaultCopy.GARAGE_MANAGE)
    onNodeWithText(VaultCopy.GARAGE_MANAGE).performClick()
    awaitText(VaultCopy.TITLE)
}

/** Open the add flow from a row's own "Add" pill, so the type arrives pre-selected. */
internal fun VaultTestRule.addFromRow(type: DocumentType) {
    onNodeWithTag(DocumentVaultTestTags.rowAction(type)).performClick()
    awaitText(VaultCopy.ADD_TITLE)
}

/** Open the add flow from the bar at the bottom, which names no type. */
internal fun VaultTestRule.addFromBar() {
    onNodeWithText(VaultCopy.ADD_DOCUMENT_BAR).performClick()
    awaitText(VaultCopy.ADD_TITLE)
}

/**
 * Assert that one row says [text] — its status, its pill or its name.
 *
 * Matched against the unmerged tree: a row with a document in it is clickable and merges its
 * children away, while a missing row is not and keeps them. Only the unmerged tree looks the
 * same for both.
 */
internal fun VaultTestRule.assertRowShows(type: DocumentType, text: String) {
    onNode(
        hasTestTag(DocumentVaultTestTags.row(type)) and hasAnyDescendant(hasText(text)),
        useUnmergedTree = true,
    ).assertExists()
}

/** Open one document's detail from its row. */
internal fun VaultTestRule.openDocument(type: DocumentType) {
    onNodeWithTag(DocumentVaultTestTags.row(type)).performClick()
    awaitLabel(VaultCopy.MENU_LABEL)
}

/** Open the detail screen's overflow menu. */
internal fun VaultTestRule.openDocumentMenu() {
    onNodeWithLabel(VaultCopy.MENU_LABEL).performClick()
    awaitText(VaultCopy.MENU_DELETE)
}

/**
 * Pick a file through the "Upload a file" card, with the picker stubbed.
 *
 * Waits for the card first: the add screen animates in, and tapping mid-transition lands on
 * whatever the previous screen had there.
 *
 * This no longer files anything on its own — an upload lands on the confirm step, where the
 * dates are read off the paper. Use [fileAnUploadedDocument] to go all the way through.
 */
internal fun VaultTestRule.uploadAFile() {
    stubPickedFile()
    awaitText(VaultCopy.CAPTURE_UPLOAD)
    onNodeWithText(VaultCopy.CAPTURE_UPLOAD).performClick()
}

/**
 * Upload a file and confirm it, for a paper that never expires.
 *
 * Only for those: a document that renews cannot be saved without an expiry date, and the
 * stub file has no readable date on it, so there would be nothing to save. Driving the date
 * picker is left to the tests that are about the date itself.
 */
internal fun VaultTestRule.fileAnUploadedDocument() {
    uploadAFile()
    awaitText(VaultCopy.REVIEW_TITLE)
    onNodeWithText(VaultCopy.REVIEW_SAVE).performClick()
}

/** The document name the vault and the success screen use for a type. */
internal fun documentName(type: DocumentType): String = when (type) {
    DocumentType.INSURANCE -> VaultCopy.DOC_INSURANCE
    DocumentType.PUC -> VaultCopy.DOC_PUC
    DocumentType.RC -> VaultCopy.DOC_RC
    DocumentType.LICENCE -> VaultCopy.DOC_LICENCE
    DocumentType.LOAN -> "Loan papers"
    DocumentType.OTHER -> "Document"
}

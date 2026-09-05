package com.hopcape.odo.infrastructure.database.support

import com.hopcape.odo.core.domain.support.TicketAttachment
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An attachment as the `attachments` column holds it.
 *
 * Its own type rather than serializing the domain one: the column's shape is a storage
 * decision that the server also reads, and a rename in the kernel must not silently change
 * what is already on thousands of devices.
 */
@Serializable
internal data class StoredAttachment(
    @SerialName("storage_key") val storageKey: String,
    @SerialName("name") val name: String,
)

internal fun TicketAttachment.toStored() = StoredAttachment(storageKey = storageKey, name = name)

internal fun StoredAttachment.toDomain() = TicketAttachment(storageKey = storageKey, name = name)

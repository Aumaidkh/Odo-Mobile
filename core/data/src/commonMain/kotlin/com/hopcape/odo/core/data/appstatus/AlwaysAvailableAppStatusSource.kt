package com.hopcape.odo.core.data.appstatus

import com.hopcape.odo.core.domain.appstatus.AppStatus
import com.hopcape.odo.core.domain.appstatus.AppStatusSource

/**
 * The [AppStatusSource] in force while nothing reads a real remote — the same "development
 * stub until the real adapter lands" role [com.hopcape.odo.core.data.entitlement.AlwaysProEntitlement]
 * plays for entitlements.
 *
 * Always answers [AppStatus.Unknown]: min version `0`, maintenance `OFF`. Every build that
 * has no `google-services.json` (CI, a fresh checkout) permanently uses this — see
 * `:infrastructure:firebase:remoteconfig`'s `RemoteConfigAppStatusSource`, which replaces
 * this binding when Firebase is configured.
 */
internal class AlwaysAvailableAppStatusSource : AppStatusSource {
    override suspend fun fetch(): AppStatus = AppStatus.Unknown
}

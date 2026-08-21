package com.hopcape.odo.feature.support.presentation.licences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoChip
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.support.resources.Res
import com.hopcape.odo.feature.support.resources.sp_lic_intro
import com.hopcape.odo.feature.support.resources.sp_lic_read
import com.hopcape.odo.feature.support.resources.sp_licences
import org.jetbrains.compose.resources.stringResource

/**
 * What Odo is built on, grouped by the licence each dependency is used under.
 *
 * Grouped rather than listed flat: the licence is the part that carries legal meaning, and
 * a reader is here to find out what terms apply rather than to read an alphabetical list.
 *
 * Each group links out to the licence's own published text. Naming a licence without a way
 * to read it is the common shortcut and a poor one — the text is the attribution, not the
 * name — and these documents are hosted by the bodies that wrote them, so linking is
 * honest where bundling a copied text would go stale.
 *
 * @param onOpenLicence opens the licence's full text in a browser.
 */
@Composable
internal fun LicencesScreen(onBack: () -> Unit, onOpenLicence: (String) -> Unit) {
    val groups = licencedLibrariesByLicence().entries.toList()

    OdoScreen(title = stringResource(Res.string.sp_licences), onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        ) {
            item {
                OdoText(
                    stringResource(Res.string.sp_lic_intro),
                    style = OdoTheme.typography.body,
                    color = OdoTheme.colors.textDim,
                )
            }

            items(groups.size, key = { groups[it].key.name }) { index ->
                val (licence, libraries) = groups[index]
                OdoCard {
                    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                        OdoText(licence.name, style = OdoTheme.typography.heading)
                        libraries.forEach { library ->
                            OdoText(
                                library,
                                style = OdoTheme.typography.body,
                                color = OdoTheme.colors.textDim,
                            )
                        }
                        OdoChip(
                            stringResource(Res.string.sp_lic_read, licence.name),
                            onClick = { onOpenLicence(licence.url) },
                        )
                    }
                }
            }
        }
    }
}

package com.hopcape.odo.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.sync.SyncStatusProvider
import com.hopcape.odo.core.platform.app.AppInfo
import com.hopcape.odo.feature.profile.domain.model.ProfileSnapshot
import com.hopcape.odo.feature.profile.domain.usecase.ObserveProfileUseCase
import com.hopcape.odo.feature.profile.presentation.state.Loadable
import com.hopcape.odo.feature.profile.resources.Res
import com.hopcape.odo.feature.profile.resources.pf_error_read_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the profile home.
 *
 * Observes rather than reads once: an owner who sets their city on the edit screen comes
 * straight back here, and the city prompt has to be gone when they arrive.
 */
internal class ProfileViewModel(
    private val observeProfile: ObserveProfileUseCase,
    appInfo: AppInfo,
    syncStatus: SyncStatusProvider,
    private val telemetry: ProfileTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState(version = appInfo.versionName))
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    /** The opened event is worth one per visit, not one per emission of the profile flow. */
    private var reported = false

    init {
        // Three separate flows because they change for different reasons: a run starting,
        // a row being written, and a pull finishing.
        viewModelScope.launch {
            syncStatus.isSyncing.collect { running ->
                _state.update { it.copy(sync = it.sync.copy(isSyncing = running)) }
            }
        }
        viewModelScope.launch {
            syncStatus.pendingCount.collect { count ->
                _state.update { it.copy(sync = it.sync.copy(pendingCount = count)) }
            }
        }
        viewModelScope.launch {
            syncStatus.lastSyncedAt.collect { at ->
                _state.update { it.copy(sync = it.sync.copy(lastSyncedAt = at)) }
            }
        }
        viewModelScope.launch {
            syncStatus.lastError.collect { error ->
                _state.update { it.copy(sync = it.sync.copy(lastError = error)) }
            }
        }

        observe()
    }

    fun onEvent(event: ProfileEvent) = when (event) {
        ProfileEvent.SignInStarted -> telemetry.signInStarted()
    }

    private fun observe() {
        viewModelScope.launch {
            observeProfile()
                .catch { cause ->
                    telemetry.readFailed(ProfileTelemetry.Screen.HOME, cause)
                    _state.update { it.copy(content = Loadable.Failed(UiText(Res.string.pf_error_read_failed))) }
                }
                .collect { snapshot ->
                    if (!reported) {
                        reported = true
                        telemetry.profileOpened(
                            isPro = snapshot.isPro,
                            isSignedIn = snapshot.isSignedIn,
                            hasCity = snapshot.city != null,
                        )
                    }
                    _state.update { it.copy(content = Loadable.Ready(snapshot.toContent())) }
                }
        }
    }
}

private fun ProfileSnapshot.toContent(): ProfileContent = ProfileContent(
    name = name,
    city = city,
    avatarPath = avatarPath,
    isPro = isPro,
    isSignedIn = isSignedIn,
    notificationTopicsOn = settings.notifications.enabledTopics,
    distanceUnit = settings.distanceUnit,
    fuelEfficiencyUnit = settings.fuelEfficiencyUnit,
    theme = settings.theme,
)

package com.hopcape.odo.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.profile.domain.model.SUPPORTED_CITIES
import com.hopcape.odo.feature.profile.domain.usecase.DeleteAllDataUseCase
import com.hopcape.odo.feature.profile.domain.usecase.ObserveProfileUseCase
import com.hopcape.odo.feature.profile.domain.usecase.OwnerDetailsCommand
import com.hopcape.odo.feature.profile.domain.usecase.SetAvatarUseCase
import com.hopcape.odo.feature.profile.domain.usecase.UpdateOwnerDetailsUseCase
import com.hopcape.odo.feature.profile.presentation.state.Submission
import com.hopcape.odo.feature.profile.presentation.state.text
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the edit-profile screen.
 *
 * Seeded once rather than observed: the fields are being typed into, and an emission from
 * the profile flow mid-edit would overwrite what the owner is halfway through writing.
 */
internal class EditProfileViewModel(
    private val observeProfile: ObserveProfileUseCase,
    private val updateDetails: UpdateOwnerDetailsUseCase,
    private val setAvatar: SetAvatarUseCase,
    private val deleteAllData: DeleteAllDataUseCase,
    private val telemetry: ProfileTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(EditProfileUiState(cities = SUPPORTED_CITIES))
    val state: StateFlow<EditProfileUiState> = _state.asStateFlow()

    private val _effects = Channel<EditProfileEffect>(Channel.BUFFERED)
    val effects: Flow<EditProfileEffect> = _effects.receiveAsFlow()

    /** The city on file when the screen opened, so a save can report whether it moved. */
    private var cityOnOpen: String? = null

    init {
        telemetry.settingsOpened(ProfileTelemetry.Screen.EDIT)
        load()
    }

    fun onEvent(event: EditProfileEvent) = when (event) {
        is EditProfileEvent.NameChanged -> _state.update { it.copy(name = it.name.update(event.value)) }
        is EditProfileEvent.EmailChanged -> _state.update { it.copy(email = it.email.update(event.value)) }
        is EditProfileEvent.CityChanged -> _state.update { it.copy(city = it.city.update(event.value)) }
        is EditProfileEvent.PhotoPicked -> savePhoto(event.pickedRef)
        EditProfileEvent.Save -> save()
        EditProfileEvent.DeleteRequested -> _state.update { it.copy(confirmingDelete = true) }
        EditProfileEvent.DeleteDismissed -> _state.update { it.copy(confirmingDelete = false) }
        EditProfileEvent.DeleteConfirmed -> deleteEverything()
    }

    private fun load() {
        viewModelScope.launch {
            val snapshot = observeProfile()
                .catch { cause -> telemetry.readFailed(ProfileTelemetry.Screen.EDIT, cause) }
                .first()
            cityOnOpen = snapshot.city
            _state.update {
                it.copy(
                    name = it.name.update(snapshot.name),
                    email = it.email.update(snapshot.email),
                    city = it.city.update(snapshot.city),
                    phoneNumber = snapshot.phoneNumber,
                    avatarPath = snapshot.avatarPath,
                    isSignedIn = snapshot.isSignedIn,
                )
            }
        }
    }

    private fun save() {
        val current = _state.value
        if (!current.canSave) return

        _state.update { it.copy(submission = Submission.InFlight) }
        viewModelScope.launch(telemetry.op(ProfileTelemetry.Trace.SAVE_DETAILS)) {
            val command = OwnerDetailsCommand(
                name = current.name.text,
                email = current.email.text,
                city = current.city.value,
            )
            val cityChanged = current.city.value != cityOnOpen
            telemetry.detailsSave(cityChanged) { updateDetails(command) }.fold(
                ifLeft = { errors -> _state.update { it.withErrors(errors.toList()) } },
                ifRight = {
                    cityOnOpen = current.city.value
                    _state.update { it.copy(submission = Submission.Succeeded) }
                    _effects.trySend(EditProfileEffect.Saved)
                },
            )
        }
    }

    private fun savePhoto(pickedRef: String) {
        viewModelScope.launch(telemetry.op(ProfileTelemetry.Trace.SAVE_AVATAR)) {
            telemetry.avatarSave { setAvatar(pickedRef) }.fold(
                ifLeft = { error ->
                    _state.update { it.copy(submission = Submission.Failed(error.toProfileMessage())) }
                },
                ifRight = { profile -> _state.update { it.copy(avatarPath = profile.avatarPath) } },
            )
        }
    }

    private fun deleteEverything() {
        _state.update { it.copy(confirmingDelete = false, deletion = Submission.InFlight) }
        viewModelScope.launch(telemetry.op(ProfileTelemetry.Trace.DELETE_DATA)) {
            telemetry.dataDeleted { deleteAllData() }.fold(
                ifLeft = { error ->
                    _state.update { it.copy(deletion = Submission.Failed(error.toProfileMessage())) }
                },
                ifRight = {
                    _state.update { it.copy(deletion = Submission.Succeeded) }
                    _effects.trySend(EditProfileEffect.DataDeleted)
                },
            )
        }
    }
}

/**
 * Put each failure on the field it belongs to, and anything else on the form as a whole.
 *
 * A name error under the name field is correctable; the same words in a banner are not.
 */
private fun EditProfileUiState.withErrors(errors: List<DomainError>): EditProfileUiState {
    var state = copy(submission = Submission.Idle)
    errors.forEach { error ->
        state = when (error) {
            DomainError.BlankOwnerName,
            is DomainError.OwnerNameTooShort,
            is DomainError.OwnerNameTooLong,
            -> state.copy(name = state.name.fail(error.toProfileMessage()))

            DomainError.InvalidOwnerEmail,
            is DomainError.OwnerEmailTooLong,
            -> state.copy(email = state.email.fail(error.toProfileMessage()))

            else -> state.copy(submission = Submission.Failed(error.toProfileMessage()))
        }
    }
    return state
}

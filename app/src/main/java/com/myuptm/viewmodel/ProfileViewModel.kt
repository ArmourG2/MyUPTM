package com.myuptm.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myuptm.domain.model.UserProfile
import com.myuptm.domain.repository.CloudinaryRepository
import com.myuptm.domain.repository.FileType
import com.myuptm.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val userProfile: UserProfile? = null,
    val isUploading: Boolean = false,
    val uploadedImageUrl: String? = null,
    val uploadError: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val cloudinaryRepository: CloudinaryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = profileRepository.getUserProfile()
            result.onSuccess { profile ->
                _uiState.value = _uiState.value.copy(isLoading = false, userProfile = profile)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load profile"
                )
            }
        }
    }

    fun uploadAvatar(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true, uploadError = null, uploadedImageUrl = null)
            val result = cloudinaryRepository.uploadFile(uri, FileType.IMAGE)
            result.onSuccess { url ->
                // Sprint 8 Task 3: persist the URL, then refresh so the avatar renders.
                val saveResult = profileRepository.updateAvatarUrl(url)
                if (saveResult.isSuccess) {
                    _uiState.value = _uiState.value.copy(isUploading = false, uploadedImageUrl = url, uploadError = null)
                    loadProfile()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        uploadError = "Image uploaded but not saved: ${saveResult.exceptionOrNull()?.message}"
                    )
                }
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isUploading = false, uploadError = e.message)
            }
        }
    }
}
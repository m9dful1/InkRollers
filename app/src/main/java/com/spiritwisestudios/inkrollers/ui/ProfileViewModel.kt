package com.spiritwisestudios.inkrollers.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spiritwisestudios.inkrollers.repository.ProfileRepository
import com.spiritwisestudios.inkrollers.model.PlayerProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository
) : ViewModel() {
    
    private val _currentProfile = MutableLiveData<PlayerProfile?>()
    val currentProfile: LiveData<PlayerProfile?> = _currentProfile
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    private val _saveSuccess = MutableLiveData<Boolean>()
    val saveSuccess: LiveData<Boolean> = _saveSuccess
    
    private val _friendProfiles = MutableLiveData<List<FriendDisplay>>()
    val friendProfiles: LiveData<List<FriendDisplay>> = _friendProfiles

    fun loadProfile(uid: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                val profile = profileRepository.loadPlayerProfile(uid)
                if (profile != null) {
                    _currentProfile.value = profile
                    loadFriends(profile.friends)
                } else {
                    // Generate new profile
                    generateUniqueProfile(uid)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load profile: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun saveProfile(playerName: String, catchPhrase: String, favoriteColors: List<Int>) {
        val profile = _currentProfile.value ?: return
        
        if (favoriteColors.size != 3 || favoriteColors.toSet().size != 3) {
            _errorMessage.value = "Must select 3 distinct colors"
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            
            try {
                val updatedProfile = profile.copy(
                    playerName = playerName,
                    catchPhrase = catchPhrase,
                    favoriteColors = favoriteColors
                )
                
                val success = profileRepository.savePlayerProfile(updatedProfile)
                if (success) {
                    _currentProfile.value = updatedProfile
                    _saveSuccess.value = true
                } else {
                    _errorMessage.value = "Failed to save profile"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error saving profile: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun addFriendByCode(friendCode: String) {
        val currentProfile = _currentProfile.value ?: return
        
        if (friendCode == currentProfile.friendCode) {
            _errorMessage.value = "That's your own code!"
            return
        }
        
        viewModelScope.launch {
            try {
                val friendProfile = profileRepository.findProfileByFriendCode(friendCode)
                if (friendProfile != null) {
                    val friends = currentProfile.friends.toMutableList()
                    if (friendProfile.uid in friends) {
                        _errorMessage.value = "Already friends!"
                    } else {
                        friends.add(friendProfile.uid)
                        val updatedProfile = currentProfile.copy(friends = friends)
                        val success = profileRepository.savePlayerProfile(updatedProfile)
                        if (success) {
                            _currentProfile.value = updatedProfile
                            loadFriends(friends)
                        } else {
                            _errorMessage.value = "Failed to add friend"
                        }
                    }
                } else {
                    _errorMessage.value = "No user found with that code"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error adding friend: ${e.message}"
            }
        }
    }
    
    fun removeFriend(friendUid: String) {
        val currentProfile = _currentProfile.value ?: return
        
        viewModelScope.launch {
            try {
                val newFriends = currentProfile.friends.filter { it != friendUid }
                val updatedProfile = currentProfile.copy(friends = newFriends)
                val success = profileRepository.savePlayerProfile(updatedProfile)
                if (success) {
                    _currentProfile.value = updatedProfile
                    loadFriends(newFriends)
                } else {
                    _errorMessage.value = "Failed to remove friend"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error removing friend: ${e.message}"
            }
        }
    }
    
    private suspend fun generateUniqueProfile(uid: String) {
        var attempts = 0
        val maxAttempts = 10
        
        while (attempts < maxAttempts) {
            attempts++
            val potentialCode = generateFriendCodeInternal(uid, attempts)
            
            try {
                val isUnique = profileRepository.isFriendCodeUnique(potentialCode)
                if (isUnique) {
                    val newProfile = PlayerProfile(
                        uid = uid,
                        friendCode = potentialCode,
                        playerName = "New Player"
                    )
                    _currentProfile.value = newProfile
                    // Save the new profile immediately
                    profileRepository.savePlayerProfile(newProfile)
                    return
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error generating unique profile: ${e.message}"
                return
            }
        }
        
        // Fallback if max attempts reached
        val fallbackCode = generateFriendCodeInternal(uid, attempts)
        val newProfile = PlayerProfile(
            uid = uid,
            friendCode = fallbackCode,
            playerName = "New Player"
        )
        _currentProfile.value = newProfile
        profileRepository.savePlayerProfile(newProfile)
    }
    
    private fun generateFriendCodeInternal(uid: String, attempt: Int = 0): String {
        val rawCodeMaterial = (uid.hashCode().toUInt() + attempt.toUInt()).toString(36).uppercase()
        val baseCode = rawCodeMaterial.padStart(6, '0').take(6)
        val filteredCode = baseCode.filter { it.isLetterOrDigit() }
        return filteredCode.take(6).padEnd(6, ('A'..'Z').random())
    }
    
    private suspend fun loadFriends(friendUids: List<String>) {
        if (friendUids.isEmpty()) {
            _friendProfiles.value = emptyList()
            return
        }
        
        try {
            val friendDisplays = mutableListOf<FriendDisplay>()
            for (uid in friendUids) {
                val friendProfile = profileRepository.loadPlayerProfile(uid)
                if (friendProfile != null) {
                    friendDisplays.add(
                        FriendDisplay(
                            uid = friendProfile.uid,
                            name = friendProfile.playerName,
                            friendCode = friendProfile.friendCode,
                            winCount = friendProfile.winCount,
                            lossCount = friendProfile.lossCount,
                            isOnline = friendProfile.isOnline
                        )
                    )
                }
            }
            friendDisplays.sortBy { it.name }
            _friendProfiles.value = friendDisplays
        } catch (e: Exception) {
            _errorMessage.value = "Error loading friends: ${e.message}"
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    fun clearSaveSuccess() {
        _saveSuccess.value = false
    }
} 
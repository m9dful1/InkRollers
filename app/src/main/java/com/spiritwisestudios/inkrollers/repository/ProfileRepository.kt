package com.spiritwisestudios.inkrollers.repository

import com.google.firebase.database.FirebaseDatabase
import com.spiritwisestudios.inkrollers.model.PlayerProfile
import kotlinx.coroutines.tasks.await

object ProfileRepository {
    private val profilesRef = FirebaseDatabase.getInstance().getReference("profiles")

    suspend fun savePlayerProfile(profile: PlayerProfile): Boolean {
        return try {
            profilesRef.child(profile.uid).setValue(profile).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun loadPlayerProfile(uid: String): PlayerProfile? {
        return try {
            val snapshot = profilesRef.child(uid).get().await()
            snapshot.getValue(PlayerProfile::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun findProfileByFriendCode(friendCode: String): PlayerProfile? {
        return try {
            val snapshot = profilesRef.orderByChild("friendCode").equalTo(friendCode)
                .limitToFirst(1)
                .get()
                .await()
            snapshot.children.firstOrNull()?.getValue(PlayerProfile::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun isFriendCodeUnique(friendCode: String): Boolean {
        return try {
            val snapshot = profilesRef.orderByChild("friendCode").equalTo(friendCode)
                .limitToFirst(1)
                .get()
                .await()
            !snapshot.exists()
        } catch (e: Exception) {
            false // Assume not unique on error
        }
    }

    fun setUserOnlineStatus(uid: String) {
        if (uid.isEmpty()) return
        val profileRef = profilesRef.child(uid)
        profileRef.child("isOnline").setValue(true)
        profileRef.child("isOnline").onDisconnect().setValue(false)
    }

    fun setUserOfflineStatus(uid: String) {
        if (uid.isEmpty()) return
        val profileRef = profilesRef.child(uid)
        profileRef.child("isOnline").setValue(false)
    }

    /** Updates the player's current lobby ID in their profile. */
    fun updatePlayerLobby(uid: String, lobbyId: String?, onComplete: (Boolean) -> Unit) {
        if (uid.isEmpty()) {
            onComplete(false)
            return
        }
        val profileRef = profilesRef.child(uid)
        profileRef.child("currentLobbyId").setValue(lobbyId)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    /** Configures onDisconnect to clear the player's lobby ID. */
    fun setLobbyOnDisconnect(uid: String) {
        if (uid.isEmpty()) return
        val lobbyRef = profilesRef.child(uid).child("currentLobbyId")
        lobbyRef.onDisconnect().setValue(null)
    }

    /** Cancels any pending onDisconnect operations for the player's lobby ID. */
    fun cancelLobbyOnDisconnect(uid: String) {
        if (uid.isEmpty()) return
        val lobbyRef = profilesRef.child(uid).child("currentLobbyId")
        lobbyRef.onDisconnect().cancel()
    }
} 
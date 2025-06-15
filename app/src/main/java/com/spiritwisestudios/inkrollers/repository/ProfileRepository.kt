package com.spiritwisestudios.inkrollers.repository

import com.google.firebase.database.FirebaseDatabase
import com.spiritwisestudios.inkrollers.model.PlayerProfile

/**
 * Repository for managing player profile data in Firebase Realtime Database.
 * Provides methods for creating, reading, updating, and deleting player profiles,
 * as well as handling friend codes and online status.
 */
object ProfileRepository {
    /** Saves a player's complete profile to Firebase under their unique UID. */
    fun savePlayerProfile(profile: PlayerProfile, onComplete: (Boolean) -> Unit) {
        val uid = profile.uid
        val ref = FirebaseDatabase.getInstance().getReference("profiles").child(uid)
        ref.setValue(profile)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    /** Loads a player's profile from Firebase using their UID. */
    fun loadPlayerProfile(uid: String, onResult: (PlayerProfile?) -> Unit) {
        val ref = FirebaseDatabase.getInstance().getReference("profiles").child(uid)
        ref.get().addOnSuccessListener { snapshot ->
            val profile = snapshot.getValue(PlayerProfile::class.java)
            onResult(profile)
        }.addOnFailureListener {
            onResult(null)
        }
    }

    /** Finds a player profile by their unique 6-character friend code. */
    fun findProfileByFriendCode(friendCode: String, onResult: (PlayerProfile?, Exception?) -> Unit) {
        val ref = FirebaseDatabase.getInstance().getReference("profiles")
        ref.orderByChild("friendCode").equalTo(friendCode)
            .limitToFirst(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val profile = snapshot.children.firstOrNull()?.getValue(PlayerProfile::class.java)
                onResult(profile, null)
            }
            .addOnFailureListener { exception -> onResult(null, exception) }
    }

    /** Checks if a given friend code is already in use by another profile. */
    fun isFriendCodeUnique(friendCode: String, onResult: (Boolean, Exception?) -> Unit) {
        val ref = FirebaseDatabase.getInstance().getReference("profiles")
        ref.orderByChild("friendCode").equalTo(friendCode)
            .limitToFirst(1)
            .get()
            .addOnSuccessListener { snapshot ->
                onResult(!snapshot.exists(), null) // True if no snapshot exists (code is unique)
            }
            .addOnFailureListener { exception ->
                onResult(false, exception)
            }
    }

    /** Sets the user's online status to true and configures onDisconnect to set it to false. */
    fun setUserOnlineStatus(uid: String) {
        if (uid.isEmpty()) return
        val profileRef = FirebaseDatabase.getInstance().getReference("profiles").child(uid)
        profileRef.child("isOnline").setValue(true)
        profileRef.child("isOnline").onDisconnect().setValue(false) // Set to false when disconnected
    }

    /** Explicitly sets the user's online status to false. */
    fun setUserOfflineStatus(uid: String) {
        if (uid.isEmpty()) return
        val profileRef = FirebaseDatabase.getInstance().getReference("profiles").child(uid)
        profileRef.child("isOnline").setValue(false)
        // Optional: cancel onDisconnect if explicitly signing out but app remains open.
        // profileRef.child("isOnline").onDisconnect().cancel()
    }

    /** Updates the player's current lobby ID in their profile. */
    fun updatePlayerLobby(uid: String, lobbyId: String?, onComplete: (Boolean) -> Unit) {
        if (uid.isEmpty()) {
            onComplete(false)
            return
        }
        val profileRef = FirebaseDatabase.getInstance().getReference("profiles").child(uid)
        profileRef.child("currentLobbyId").setValue(lobbyId)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    /** Configures onDisconnect to clear the player's lobby ID. */
    fun setLobbyOnDisconnect(uid: String) {
        if (uid.isEmpty()) return
        val lobbyRef = FirebaseDatabase.getInstance().getReference("profiles").child(uid).child("currentLobbyId")
        lobbyRef.onDisconnect().setValue(null)
    }

    /** Cancels any pending onDisconnect operations for the player's lobby ID. */
    fun cancelLobbyOnDisconnect(uid: String) {
        if (uid.isEmpty()) return
        val lobbyRef = FirebaseDatabase.getInstance().getReference("profiles").child(uid).child("currentLobbyId")
        lobbyRef.onDisconnect().cancel()
    }
} 
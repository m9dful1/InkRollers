package com.spiritwisestudios.inkrollers.repository

import com.google.firebase.database.FirebaseDatabase
import com.spiritwisestudios.inkrollers.model.PlayerProfile

object ProfileRepository {
    fun savePlayerProfile(profile: PlayerProfile, onComplete: (Boolean) -> Unit) {
        val uid = profile.uid
        val ref = FirebaseDatabase.getInstance().getReference("profiles").child(uid)
        ref.setValue(profile)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun loadPlayerProfile(uid: String, onResult: (PlayerProfile?) -> Unit) {
        val ref = FirebaseDatabase.getInstance().getReference("profiles").child(uid)
        ref.get().addOnSuccessListener { snapshot ->
            val profile = snapshot.getValue(PlayerProfile::class.java)
            onResult(profile)
        }.addOnFailureListener {
            onResult(null)
        }
    }

    fun findProfileByFriendCode(friendCode: String, onResult: (PlayerProfile?) -> Unit) {
        val ref = FirebaseDatabase.getInstance().getReference("profiles")
        ref.orderByChild("friendCode").equalTo(friendCode)
            .limitToFirst(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val profile = snapshot.children.firstOrNull()?.getValue(PlayerProfile::class.java)
                onResult(profile)
            }
            .addOnFailureListener { onResult(null) }
    }

    fun isFriendCodeUnique(friendCode: String, onResult: (Boolean) -> Unit) {
        val ref = FirebaseDatabase.getInstance().getReference("profiles")
        ref.orderByChild("friendCode").equalTo(friendCode)
            .limitToFirst(1)
            .get()
            .addOnSuccessListener { snapshot ->
                onResult(!snapshot.exists()) // True if no snapshot exists (code is unique)
            }
            .addOnFailureListener {
                onResult(false) // Assume not unique on error to be safe, or handle error differently
            }
    }

    fun setUserOnlineStatus(uid: String) {
        if (uid.isEmpty()) return
        val profileRef = FirebaseDatabase.getInstance().getReference("profiles").child(uid)
        profileRef.child("isOnline").setValue(true)
        profileRef.child("isOnline").onDisconnect().setValue(false) // Set to false when disconnected
    }

    fun setUserOfflineStatus(uid: String) {
        if (uid.isEmpty()) return
        val profileRef = FirebaseDatabase.getInstance().getReference("profiles").child(uid)
        profileRef.child("isOnline").setValue(false)
        // Optional: cancel onDisconnect if explicitly signing out but app remains open.
        // profileRef.child("isOnline").onDisconnect().cancel()
    }
} 
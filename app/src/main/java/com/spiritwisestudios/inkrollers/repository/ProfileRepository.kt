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
} 
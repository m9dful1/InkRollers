package com.spiritwisestudios.inkrollers.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.spiritwisestudios.inkrollers.R
import com.spiritwisestudios.inkrollers.model.PlayerColorPalette
import com.spiritwisestudios.inkrollers.model.PlayerProfile
import com.spiritwisestudios.inkrollers.repository.ProfileRepository
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.spiritwisestudios.inkrollers.ui.FriendAdapter
import com.spiritwisestudios.inkrollers.ui.FriendDisplay
import android.util.Log
import android.widget.Toast

class ProfileFragment : Fragment() {
    private lateinit var editPlayerName: TextInputEditText
    private lateinit var editCatchPhrase: TextInputEditText
    private lateinit var textFriendCode: TextView
    private lateinit var btnCopyFriendCode: ImageButton
    private lateinit var colorPicker1: FrameLayout
    private lateinit var colorPicker2: FrameLayout
    private lateinit var colorPicker3: FrameLayout
    private lateinit var textWinLoss: TextView
    private lateinit var recyclerFriends: RecyclerView
    private lateinit var editAddFriendCode: TextInputEditText
    private lateinit var btnAddFriend: Button
    private lateinit var btnSaveProfile: Button

    // State
    private var selectedColors = mutableListOf<Int?>(null, null, null)
    private var currentProfile: PlayerProfile? = null
    private var colorPickerDialog: android.app.AlertDialog? = null
    private var friendDisplays: MutableList<FriendDisplay> = mutableListOf()
    private var friendAdapter: FriendAdapter? = null

    companion object {
        private const val ARG_UID = "user_uid"

        fun newInstance(uid: String): ProfileFragment {
            val fragment = ProfileFragment()
            val args = Bundle()
            args.putString(ARG_UID, uid)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Toast.makeText(requireContext(), "ProfileFragment onViewCreated!", Toast.LENGTH_LONG).show()
        Log.d("ProfileFragment", "onViewCreated CALLED")

        // Bind UI
        editPlayerName = view.findViewById(R.id.edit_player_name)
        editCatchPhrase = view.findViewById(R.id.edit_catch_phrase)
        textFriendCode = view.findViewById(R.id.text_friend_code)
        btnCopyFriendCode = view.findViewById(R.id.btn_copy_friend_code)
        colorPicker1 = view.findViewById(R.id.color_picker_1)
        colorPicker2 = view.findViewById(R.id.color_picker_2)
        colorPicker3 = view.findViewById(R.id.color_picker_3)
        textWinLoss = view.findViewById(R.id.text_win_loss)
        recyclerFriends = view.findViewById(R.id.recycler_friends)
        editAddFriendCode = view.findViewById(R.id.edit_add_friend_code)
        btnAddFriend = view.findViewById(R.id.btn_add_friend)
        btnSaveProfile = view.findViewById(R.id.btn_save_profile)

        // Disable save button initially
        btnSaveProfile.isEnabled = false

        // Load profile
        val uid = arguments?.getString(ARG_UID)
        Log.d("ProfileFragment", "Attempting to load profile. UID from arguments: $uid")

        if (uid == null) {
            Toast.makeText(requireContext(), "User not signed in. Cannot load profile.", Toast.LENGTH_LONG).show()
            Log.e("ProfileFragment", "UID is null. ProfileFragment will not initialize further.")
            return // Essential to stop further execution if no user
        }

        ProfileRepository.loadPlayerProfile(uid) { profile ->
            Log.i("ProfileFragment", "ProfileRepository.loadPlayerProfile callback executed. Profile is null? ${profile == null}")
            if (profile != null) {
                Log.d("ProfileFragment", "Existing profile loaded for UID: $uid. FriendCode: ${profile.friendCode}")
                currentProfile = profile
                populateProfile(profile)
            } else {
                Log.d("ProfileFragment", "No existing profile for UID: $uid. Generating new profile.")
                generateUniqueFriendCodeAndCreateProfile(uid)
            }
        }

        // Save profile
        btnSaveProfile.setOnClickListener {
            saveProfile()
        }

        // Copy friend code
        btnCopyFriendCode.setOnClickListener {
            val code = textFriendCode.text.toString()
            val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Friend Code", code))
            Toast.makeText(requireContext(), "Copied!", Toast.LENGTH_SHORT).show()
        }

        // Add friend
        btnAddFriend.setOnClickListener {
            val code = editAddFriendCode.text.toString().trim().uppercase()
            if (code.isNotEmpty()) {
                addFriendByCode(code)
            }
        }

        Log.d("ProfileFragment", "onViewCreated: Checking color pickers AFTER profile load initiated.")
        Log.d("ProfileFragment", "onViewCreated: colorPicker1 is findViewById null? ${view.findViewById<FrameLayout>(R.id.color_picker_1) == null}")
        Log.d("ProfileFragment", "onViewCreated: colorPicker2 is findViewById null? ${view.findViewById<FrameLayout>(R.id.color_picker_2) == null}")
        Log.d("ProfileFragment", "onViewCreated: colorPicker3 is findViewById null? ${view.findViewById<FrameLayout>(R.id.color_picker_3) == null}")
        setupColorPickers()

        recyclerFriends.layoutManager = LinearLayoutManager(requireContext())
        friendAdapter = FriendAdapter(friendDisplays) { friend ->
            removeFriend(friend)
        }
        recyclerFriends.adapter = friendAdapter
    }

    private fun populateProfile(profile: PlayerProfile) {
        activity?.runOnUiThread {
            Log.d("ProfileFragment", "populateProfile [UI Thread]: Populating UI with profile: ${profile.uid}")
            editPlayerName.setText(profile.playerName)
            editCatchPhrase.setText(profile.catchPhrase)

            Log.d("ProfileFragment", "populateProfile [UI Thread]: Received friendCode: '${profile.friendCode}'")
            if (profile.friendCode.isNotEmpty()) {
                textFriendCode.text = profile.friendCode
            } else {
                textFriendCode.text = "DEBUG: EMPTY CODE"
                Log.w("ProfileFragment", "populateProfile [UI Thread]: profile.friendCode is empty! Setting debug text.")
            }

            textWinLoss.text = "${profile.winCount} / ${profile.lossCount}"
            selectedColors = profile.favoriteColors.map { it as Int? }.toMutableList()
            while (selectedColors.size < 3) selectedColors.add(null)
            loadFriends(profile.friends)
            refreshColorPickers()
            // Enable save button once profile is loaded/populated
            Log.d("ProfileFragment", "populateProfile [UI Thread]: Enabling save button.")
            btnSaveProfile.isEnabled = true
        }
    }

    private fun loadFriends(friendUids: List<String>) {
        friendDisplays.clear()
        if (friendUids.isEmpty()) {
            friendAdapter?.notifyDataSetChanged()
            return
        }
        // Load each friend's profile from Firebase
        var loadedCount = 0
        for (uid in friendUids) {
            ProfileRepository.loadPlayerProfile(uid) { friendProfile ->
                loadedCount++
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
                if (loadedCount == friendUids.size) {
                    // All loaded
                    friendDisplays.sortBy { it.name }
                    friendAdapter?.notifyDataSetChanged()
                }
            }
        }
    }

    private fun removeFriend(friend: FriendDisplay) {
        val profile = currentProfile ?: return
        val newFriends = profile.friends.filter { it != friend.uid }
        currentProfile = profile.copy(friends = newFriends)
        saveProfile()
        loadFriends(newFriends)
    }

    private fun saveProfile() {
        val uid = Firebase.auth.currentUser?.uid ?: return
        val name = editPlayerName.text?.toString()?.trim() ?: ""
        val phrase = editCatchPhrase.text?.toString()?.trim() ?: ""

        val profileToSave = currentProfile ?: run {
            Toast.makeText(requireContext(), "Error: Profile data not available to save.", Toast.LENGTH_SHORT).show()
            return
        }

        if (profileToSave.friendCode.isEmpty()) {
            Toast.makeText(requireContext(), "Error: Friend code is missing. Cannot save.", Toast.LENGTH_SHORT).show()
            return 
        }
        // The friendCode from profileToSave (which is currentProfile) is used.
        // It is set either by loading an existing profile or by the unique generation flow.

        val colors = selectedColors.filterNotNull()
        if (colors.size != 3) {
            Toast.makeText(requireContext(), "Select 3 distinct colors", Toast.LENGTH_SHORT).show()
            return
        }
        if (colors.toSet().size != 3) {
            Toast.makeText(requireContext(), "Colors must be distinct", Toast.LENGTH_SHORT).show()
            return
        }

        // Create the updated profile object using data from UI and existing currentProfile fields
        val updatedProfile = profileToSave.copy(
            playerName = name,
            favoriteColors = colors,
            catchPhrase = phrase
            // friendCode, friends, winCount, lossCount, isOnline are taken from profileToSave (currentProfile)
        )

        ProfileRepository.savePlayerProfile(updatedProfile) { success ->
            if (success) {
                Toast.makeText(requireContext(), "Profile saved!", Toast.LENGTH_SHORT).show()
                currentProfile = updatedProfile // Update local currentProfile with the successfully saved version
            } else {
                Toast.makeText(requireContext(), "Failed to save profile", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addFriendByCode(code: String) {
        if (currentProfile == null) return
        if (code == currentProfile!!.friendCode) {
            Toast.makeText(requireContext(), "That's your own code!", Toast.LENGTH_SHORT).show()
            return
        }
        ProfileRepository.findProfileByFriendCode(code) { friendProfile ->
            if (friendProfile != null) {
                val friends = currentProfile!!.friends.toMutableList()
                if (friendProfile.uid in friends) {
                    Toast.makeText(requireContext(), "Already friends!", Toast.LENGTH_SHORT).show()
                } else {
                    friends.add(friendProfile.uid)
                    currentProfile = currentProfile!!.copy(friends = friends)
                    saveProfile() // Save profile after adding friend
                    loadFriends(friends)
                    editAddFriendCode.text?.clear() // Clear input field
                }
            } else {
                Toast.makeText(requireContext(), "No user found with that code", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun generateFriendCodeInternal(uid: String, attempt: Int = 0): String {
        val rawCodeMaterial = (uid.hashCode().toUInt() + attempt.toUInt()).toString(36).uppercase()
        val baseCode = rawCodeMaterial.padStart(6, '0').take(6)
        val filteredCode = baseCode.filter { it.isLetterOrDigit() }
        val finalCode = filteredCode.take(6).padEnd(6, ('A'..'Z').random())
        Log.i("ProfileFragment", "generateFriendCodeInternal(uid=$uid, attempt=$attempt): raw='$rawCodeMaterial', base='$baseCode', filtered='$filteredCode', final='$finalCode'")
        return finalCode
    }

    private fun generateUniqueFriendCodeAndCreateProfile(uid: String, maxAttempts: Int = 10) {
        var attempts = 0
        Log.d("ProfileFragment", "Attempting to generate unique friend code for UID: $uid")
        fun tryGenerate() {
            if (attempts >= maxAttempts) {
                val fallbackCode = generateFriendCodeInternal(uid, attempts)
                Log.e("ProfileFragment", "Max attempts ($maxAttempts) reached for unique code. Using fallback: '$fallbackCode' for $uid")
                val newProfile = PlayerProfile(uid = uid, friendCode = fallbackCode, playerName = "New Player") // Add default name
                currentProfile = newProfile
                populateProfile(newProfile)
                saveProfile() // Attempt to save the fallback profile immediately
                return
            }
            attempts++
            val potentialCode = generateFriendCodeInternal(uid, attempts)
            Log.i("ProfileFragment", "generateUnique attempt $attempts: potentialCode: '$potentialCode' for $uid")
            ProfileRepository.isFriendCodeUnique(potentialCode) { isUnique ->
                if (isUnique) {
                    Log.i("ProfileFragment", "Unique code found: '$potentialCode' for $uid")
                    val newProfile = PlayerProfile(uid = uid, friendCode = potentialCode, playerName = "New Player") // Add default name
                    currentProfile = newProfile
                    populateProfile(newProfile)
                    saveProfile() // Save the newly created profile with unique code
                } else {
                    Log.w("ProfileFragment", "Code '$potentialCode' not unique. Retrying for $uid")
                    tryGenerate() // Retry generation
                }
            }
        }
        tryGenerate() // Start the generation process
    }

    private fun setupColorPickers() {
        Log.d("ProfileFragment", "setupColorPickers called.")
        // Ensure lateinit vars are initialized
        if (!::colorPicker1.isInitialized || !::colorPicker2.isInitialized || !::colorPicker3.isInitialized) {
            Log.e("ProfileFragment", "Color pickers not initialized before setupColorPickers!")
            return
        }

        val pickers = listOf(colorPicker1, colorPicker2, colorPicker3)
        for (i in 0..2) {
            val colorToSet = selectedColors.getOrNull(i) ?: 0xFFCCCCCC.toInt()
            pickers[i].setBackgroundColor(colorToSet)
            pickers[i].visibility = View.VISIBLE // Explicitly set visible
            pickers[i].setOnClickListener {
                Log.d("ProfileFragment", "Color picker slot $i clicked.")
                showColorPickerDialog(i)
            }
            Log.d("ProfileFragment", "OnClickListener set for color picker slot $i, color: ${String.format("#%06X", 0xFFFFFF and colorToSet)}, visible: ${pickers[i].visibility == View.VISIBLE}")
        }
    }

    private fun refreshColorPickers() {
        Log.d("ProfileFragment", "refreshColorPickers called.")
        if (!::colorPicker1.isInitialized || !::colorPicker2.isInitialized || !::colorPicker3.isInitialized) {
            Log.e("ProfileFragment", "Color pickers not initialized before refreshColorPickers!")
            return
        }
        val pickers = listOf(colorPicker1, colorPicker2, colorPicker3)
        for (i in 0..2) {
            val color = selectedColors.getOrNull(i) ?: 0xFFCCCCCC.toInt()
            pickers[i].setBackgroundColor(color)
            pickers[i].visibility = View.VISIBLE // Explicitly set visible here too
             Log.d("ProfileFragment", "Refreshed color picker slot $i, color: ${String.format("#%06X", 0xFFFFFF and color)}, visible: ${pickers[i].visibility == View.VISIBLE}")
        }
    }

    private fun showColorPickerDialog(slotIndex: Int) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)
        val grid = dialogView.findViewById<GridLayout>(R.id.grid_colors)
        val usedColors = selectedColors.filterIndexed { idx, c -> idx != slotIndex }.filterNotNull().toSet()
        val context = requireContext()
        val selectedColor = selectedColors[slotIndex]
        PlayerColorPalette.COLORS.forEach { colorInt ->
            val swatch = FrameLayout(context)
            val size = resources.getDimensionPixelSize(R.dimen.color_swatch_size)
            val params = GridLayout.LayoutParams().apply {
                width = size
                height = size
                setMargins(12, 12, 12, 12)
            }
            swatch.layoutParams = params
            // Set background drawable (selected or not)
            val isSelected = colorInt == selectedColor
            val bgRes = if (isSelected) R.drawable.color_swatch_selected else R.drawable.color_swatch_bg
            swatch.background = resources.getDrawable(bgRes, null)
            // Set color overlay
            val colorView = View(context)
            colorView.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            colorView.setBackgroundColor(colorInt)
            swatch.addView(colorView)
            // Add checkmark if selected
            if (isSelected) {
                val check = ImageView(context)
                check.setImageResource(R.drawable.ic_check)
                val checkSize = (size * 0.6).toInt()
                val checkParams = FrameLayout.LayoutParams(checkSize, checkSize)
                checkParams.gravity = android.view.Gravity.CENTER
                check.layoutParams = checkParams
                swatch.addView(check)
            }
            // Elevation/shadow
            swatch.elevation = if (isSelected) 8f else 2f
            // Disable if already used in another slot
            if (colorInt in usedColors) {
                swatch.alpha = 0.3f
                swatch.isEnabled = false
            } else {
                swatch.setOnClickListener {
                    selectedColors[slotIndex] = colorInt
                    refreshColorPickers()
                    colorPickerDialog?.dismiss()
                }
            }
            grid.addView(swatch)
        }
        val builder = android.app.AlertDialog.Builder(context)
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
        colorPickerDialog = builder.create()
        colorPickerDialog?.show()
    }
} 
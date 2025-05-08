# Ink  Rollers – Design Document

*(version 0.9 – May 07, 2025)*

---

## 1. Overview

Ink Rollers is a casual, real‑time multiplayer Android game in which players control paint rollers and attempt to cover as much of an arena floor as possible. Matches support several modes (Coverage, Zones), configurable time limits, and variable maze complexities. The game relies on simple, intuitive controls plus light resource strategy (finite ink with refill mechanics). It features a host/join lobby system using Firebase Realtime Database for state synchronization.

This design document captures the current codebase (v11) and details how the pieces interact, then outlines the full implementation roadmap.

---

## 2. Current Code Architecture (v11)

### 2.1 Package & Build

*   **Namespace:** `com.spiritwisestudios.inkrollers` (Gradle `namespace` in *app/build.gradle*).
*   **AndroidX Enabled:** via `gradle.properties` (`android.useAndroidX=true`, `android.enableJetifier=true`).
*   **SDK Versions:** Minimum SDK 26, compile/target SDK 34.
*   **Build Tools:** Kotlin 1.9.0, Android Gradle Plugin (AGP) 8.9.2.
*   **Firebase:** Dependencies via BoM (Platform `33.1.2`), using `firebase-database-ktx`. Google Services plugin `4.4.2` applied.
*   **Screen Orientation:** Primarily locked to landscape via `AndroidManifest.xml`.

### 2.2 Class-Level Components

| File                      | Responsibility                                                                                                                                                              | Key Methods / Notes                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| :------------------------ | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`HomeActivity.kt`**     | App entry point (Launcher). Provides simple UI (Title, Play button -> Host/Join submenu). When hosting, presents a Match Settings dialog (Time Limit: 3/5/7 min; Maze Complexity: Low/Medium/High). Collects Game ID if joining (if left blank, triggers a search for a random available game). Passes mode (`HOST`/`JOIN`), Game ID (if joining, or null for random), Time Limit, and Maze Complexity (if hosting) to `MainActivity` via `Intent` extras. | `onCreate()` sets up UI listeners. `showMatchSettingsDialog()` presents sequential AlertDialogs for time and complexity. `startGameActivity()` launches `MainActivity` with `EXTRA_TIME_LIMIT_MINUTES` and `EXTRA_MAZE_COMPLEXITY` for hosts. Defines complexity constants (`COMPLEXITY_LOW`, `COMPLEXITY_MEDIUM`, `COMPLEXITY_HIGH`). Handles blank Game ID for random join. |
| **`MainActivity.kt`**     | Manages the main game screen lifecycle. Inflates `activity_main.xml`. Wires up UI elements. Initializes `MultiplayerManager`. Handles `Intent` extras from `HomeActivity` *after* Firebase authentication: if hosting, retrieves Time Limit and Maze Complexity; if joining, these are fetched by `MultiplayerManager`. Stores `matchDurationMs` and `mazeComplexity`. Shows "Waiting..." dialogs and triggers pre-match countdown. Handles `MultiplayerManager` callbacks. Displays rematch dialog. Coordinates game restart. | `onCreate()`, `signInAnonymouslyAndProceed()` (which then calls `handleIntentExtras()`). `handleIntentExtras()` retrieves host settings, stores them. `multiplayerManager.joinGame` callback now provides `GameSettings`. `actuallyStartMatch()` passes `mazeComplexity` to `gameView.initGame()` and uses `matchDurationMs` for `gameView.startGameMode()`. `restartMatch()` passes stored `mazeComplexity` to `gameView.initGame()`. Call to `handleIntentExtras` in `onCreate` was removed to prevent duplicate game creation. |
| **`GameView.kt`**         | Custom `SurfaceView` owning the game loop (`GameThread`) and rendering. Manages game objects (`Player`, `Level`, `VirtualJoystick`), multi-touch input, and multiplayer display synchronization. Implements `MultiplayerManager.RemoteUpdateListener`. Manages `GameThread` lifecycle. Coordinates with `GameModeManager`. Invokes `onMatchEnd` callback. | • `initGame(mazeComplexity: String)`: Now accepts `mazeComplexity`. Creates `MazeLevel` with synchronized seed and `mazeComplexity`. Other logic (clearing state, processing pending, creating `GameThread`) remains. <br>• `startGameMode(mode: GameMode, durationMs: Long)`: Uses duration set by `MainActivity`. Other methods largely unchanged. |
| **`GameThread`** (inner)  | Simple `Thread` subclass. Runs `GameView.update(deltaTime)` + `GameView.draw()` in a `while(running)` loop. Calculates `deltaTime`. Locks/unlocks `SurfaceHolder` canvas. **A new instance is created for each match.** | `run()`. Calculates `deltaTime` and passes it to `GameView.update()`.                                                                                                                                                                                                                                                                                                                                                                                          |
| **`Player.kt`**           | Represents a paint roller avatar. Tracks position (screen coords `x`,`y`), mode, ink level, color. Moves based on joystick input via `move()`, using `deltaTime` for frame-rate independent speed. Checks collision with `Level`. Paints onto `PaintSurface` or refills ink. Sends paint actions to `MultiplayerManager` with **normalized maze coordinates**. | `move(..., deltaTime: Float)`: `moveAmount` now `MOVE_SPEED * magnitude * deltaTime`. `MOVE_SPEED` constant adjusted (e.g., to `300f`) to represent units per second. Other methods unchanged. |
| **`PlayerState.kt`**      | Data class for player state synced via Firebase. Uses **normalized maze coordinates** (`normX`, `normY`) for resolution independence. Includes `color`, `mode`, `ink`, `active` flag, `mazeSeed`. Has no-arg constructor for Firebase. | No direct changes.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| **`PaintSurface.kt`**     | Off-screen `Bitmap` and `Canvas` storing painted pixels. Provides method to clear the surface.                                                                                     | No changes.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| **`Level.kt`**            | **Abstract interface** defining the contract for game levels (mazes, rooms, etc.).                                                                                               | No changes to interface.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| **`MazeLevel.kt`**        | **Implements `Level`**. Constructor now accepts `complexity: String`. Adjusts internal cell count (`cellsX`, `cellsY`) based on complexity: LOW (e.g., 8x12), MEDIUM (e.g., 10x16), HIGH (e.g., 12x20), then adapts to device orientation. Generates a perfect maze using Recursive Backtracker algorithm seeded for synchronization. Handles scaling/offsetting. Provides coordinate conversion methods. Collision uses internal `wallRects` list, with corners filled by extending wall segments. | Constructor takes `seed` and `complexity`. `init` block determines `cellsX`, `cellsY`. `buildWallRects()` modified to extend walls slightly, ensuring solid corners. Other methods largely unchanged.                                                                                                                                                                                                                                                                                                             |
| **`VirtualJoystick.kt`**  | Manages state and rendering of an on-screen virtual joystick. Calculates normalized direction and magnitude based on touch input relative to its base position.                  | No changes.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| **`InkHudView.kt`**       | Custom `View` displaying the local player's ink level (as a bar) and current mode text ("PAINT" / "FILL").                                                                         | No changes.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| **`CoverageHudView.kt`**  | Custom `View` displaying coverage percentage bars for each active player color.                                                                                                  | No changes.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| **`TimerHudView.kt`**     | Custom `View` displaying the remaining match time in MM:SS format. The text alignment changed from Center to Right to ensure it stays in the top-right corner.                   | `updateTime(ms: Long)`, `onDraw()` (text paint align set to `Paint.Align.RIGHT`, x-coord `width.toFloat()`). Positioned via `activity_main.xml`.                                                                                                                                                                                                                                                                                                                                                                                                       |
| **`MultiplayerManager.kt`** | Handles Firebase RTDB interactions. `hostGame()` now accepts `durationMs`, `complexity` and stores them in Firebase at `/games/{gameId}/matchDurationMs` and `/games/{gameId}/mazeComplexity`. `joinGame(gameId: String?, initialState, callback)` retrieves these settings and provides them in its callback (e.g., via a `GameSettings` data class); if `gameId` is null, it invokes `findRandomAvailableGame()`. Other functionalities remain. | `hostGame(initialState, durationMs, complexity, callback)` and `joinGame(gameId: String?, initialState, callback)` signatures updated. Includes `findRandomAvailableGame()` to locate an open game session. Connection test logic (`testFirebaseConnection`) refined to avoid premature error reporting on initial `false` connection state. Added `GameSettings` data class (or similar mechanism) for `joinGame` callback.                                                                                                                                                                                                                                                                                                                              |
| **`CoverageCalculator.kt`** | Static utility object to sample the `PaintSurface` bitmap, excluding maze wall areas, and calculate coverage fraction per color.                                           | No changes.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| **`GameModeManager.kt`**  | Encapsulates match timing logic. Constructor now takes `durationMs` (which can vary based on host settings). Tracks start time and duration, determines if the match `isFinished()`. Supports different modes (`GameMode` enum: `COVERAGE`, `ZONES`). | Constructor `(mode, durationMs)`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |

### 2.3 Abstract Interfaces & Inheritance

*   **`Level` Interface:** Defines the core contract for any playable area.
    *   **`MazeLevel` Class:** Implements `Level` to provide a specific maze generation and interaction logic.
*   **Android View System:**
    *   `GameView` extends `SurfaceView`, implements `SurfaceHolder.Callback`.
    *   `InkHudView`, `CoverageHudView`, `TimerHudView` extend `View`.
*   **Listener Interfaces:**
    *   `GameView` implements `MultiplayerManager.RemoteUpdateListener`.
*   **Android Activity Lifecycle:**
    *   `HomeActivity` and `MainActivity` extend `AppCompatActivity`.

### 2.4 Key Data Structures

*   **`ConcurrentHashMap<String, Player>`:** Used in `GameView` to store player objects, keyed by Firebase Player ID (e.g., "player0"). Thread-safe for handling updates from Firebase listeners.
*   **`ConcurrentHashMap<String, PlayerState>`:** Used in `GameView` (`pendingPlayerStates`) to temporarily store player states received from Firebase *before* the `currentLevel` is initialized.
*   **`ConcurrentHashMap<String, VirtualJoystick>`:** Used in `GameView` to store joystick instances, keyed by Player ID (currently only the local player has one).
*   **`PlayerState` (Data Class):** Serializable object defining the structure of player data synced via Firebase (normalized position, color, mode, ink, active status, maze seed).
*   **`GameSettings` (Data Class):** (If implemented in `MultiplayerManager`) Holds `durationMs` and `complexity` for a game, read by clients when joining.
*   **`Map<Int, Float>`:** Standard Kotlin map used for returning coverage statistics (Color Int -> Fraction Float) by `CoverageCalculator` and consumed by `CoverageHudView` and `GameView`.
*   **`List<RectF>`:** Used in `MazeLevel` to store the calculated bounding boxes for maze walls for collision detection and rendering.
*   **`Bitmap` / `Canvas`:** Core Android graphics objects used by `PaintSurface` to store and draw the painted layer.

### 2.5 System States & Runtime Flow

The application transitions through several distinct states:

1.  **Home Screen (`HomeActivity`):**
    *   Initial state after launch.
    *   Displays title and "Play" button.
    *   *Transition:* User taps "Play".
2.  **Host/Join Submenu (`HomeActivity`):**
    *   "Play" button is hidden, Host/Join options appear.
    *   If "Host New Game" is tapped:
        *   A **Match Settings dialog** appears (sequential choices for Time Limit and Maze Complexity).
        *   *Transition (Host):* User confirms settings -> Launches `MainActivity` in HOST mode with selected Time Limit and Maze Complexity as Intent extras.
    *   If "Join Game" is tapped:
        *   User enters a Game ID (or leaves it blank).
        *   *Transition (Join):* User enters valid ID (or leaves blank for random search), taps "Join Game" -> Launches `MainActivity` in JOIN mode with Game ID (or null).
3.  **Waiting (Host - `MainActivity`):**
    *   `MainActivity` is active. `signInAnonymouslyAndProceed()` completes, then `handleIntentExtras()` is called.
    *   A "Waiting for other players..." dialog is shown.
    *   `MultiplayerManager` listens for player count changes.
    *   *Transition:* Second player joins -> `onPlayerCountChanged` triggers countdown.
4.  **Waiting (Join - `MainActivity`):**
    *   `MainActivity` is active. `MultiplayerManager` reads `matchDurationMs` and `mazeComplexity` from Firebase and returns them via callback. `MainActivity` stores these settings.
    *   A "Waiting for host to start..." dialog is shown.
    *   `MultiplayerManager` listens for the start signal.
    *   *Transition:* Host sends start signal -> `onMatchStartRequested` triggers countdown.
5.  **Pre-Match Countdown (`MainActivity`):**
    *   "Waiting..." dialog dismissed.
    *   "3... 2... 1... GO" dialog/overlay shown, synchronized across clients.
    *   *Transition:* Countdown finishes -> `actuallyStartMatch` is called.
6.  **Gameplay Initialization (`MainActivity.actuallyStartMatch`)**
    *   Calls `gameView.initGame(mazeComplexity)`: Creates `MazeLevel` with the selected/received complexity, clears local state, processes pending states, creates new `GameThread`.
    *   Calls `gameView.setLocalPlayerId()`: Creates/updates local `Player` object with correct start position from `MazeLevel`, updates Firebase with correct initial `normX`/`normY` via `updateLocalPlayerPartialState`.
    *   Calls `gameView.startGameMode(GameMode.COVERAGE, matchDurationMs)`: Creates `GameModeManager` using selected/received duration. Sets `isMatchReady=true`.
    *   Calls `gameView.startGameLoop()`: Sets `thread.running=true`, starts the `GameThread`.
    *   *Transition:* `GameThread` loop starts running.
7. **Gameplay Loop (`GameView` + `GameThread`):**
    *   `GameThread` runs `update(deltaTime)`/`draw` loop. `Player.move` uses `deltaTime`.
    *   Players control rollers via `VirtualJoystick`, paint the `PaintSurface`.
    *   Player state and paint actions are synced via `MultiplayerManager` and Firebase. Remote player positions correct themselves as new `PlayerState` updates arrive and are processed by `onPlayerStateChanged` (which now has a valid `currentLevel`).
    *   HUDs (`InkHudView`, `CoverageHudView`, `TimerHudView`) display game info.
    *   `GameModeManager` tracks time.
    *   *Transition:* `GameModeManager.isFinished()` returns true -> Match End state.
8.  **Match End / Rematch (`MainActivity` + `GameView`):**
    *   `GameThread` loop stops (`thread.running = false`).
    *   Winner/Loser determined based on coverage.
    *   `onMatchEnd` callback fires.
    *   "You Won/Lost! Play Again?" dialog shown.
    *   Player selects "Yes" or "No". Answer sent via `MultiplayerManager.sendRematchAnswer`.
    *   `MultiplayerManager` waits for all answers via `setupRematchListener`.
    *   *Transition (Both Yes):* `onRematchDecision(true)` fires -> `MainActivity.restartMatch` runs -> Returns to **Gameplay Initialization** state after reset and delay.
    *   *Transition (Any No / Back Button):* `onRematchDecision(false)` fires -> `MainActivity.finish()` called -> Returns to **Home Screen**. OR `onDestroy` called -> `MultiplayerManager.leaveGame()` cleans up Firebase.

### 2.6 System Inputs & Outputs

*   **Inputs:**
    *   **User Touch:**
        *   Screen touches interpreted by `GameView.onTouchEvent` to control the `VirtualJoystick`.
        *   Button taps in `HomeActivity` (Play, Host, Join, **Match Settings Dialog choices**).
        *   Button taps in `MainActivity` (Mode Toggle, Rematch Dialog).
        *   Keyboard input in `HomeActivity` for Game ID `EditText` (can be blank to initiate a random game search).
    *   **Firebase Realtime Database Events:**
        *   Player state changes (`onChildAdded`, `onChildChanged` on `/players/`).
        *   Player disconnections (`onChildRemoved` on `/players/` or `active=false` state).
        *   New paint actions (`onChildAdded` on `/paint/`).
        *   Rematch answers (`onDataChange` on `/rematchRequests/`).
        *   Player count changes (`onDataChange` on `/players/`).
        *   Match start signal (`onDataChange` on `/started`).
*   **Outputs:**
    *   **Screen Display:**
        *   Rendered game state via `GameView` (maze, paint, players, joystick).
        *   HUD overlays via `InkHudView`, `CoverageHudView`, `TimerHudView`.
        *   UI elements in `HomeActivity` and `MainActivity` (buttons, text, dialogs).
    *   **Firebase Realtime Database Writes:**
        *   Local player's full state (`PlayerState`) updates during gameplay to `/games/{id}/players/{pid}`.
        *   Local player's *partial* state (initial `normX`, `normY`) update immediately after local initialization in `GameView` via `updateChildren`.
        *   Paint actions (including normalized coordinates) pushed to `/games/{id}/paint/`.
        *   Rematch answers written to `/games/{id}/rematchRequests/{pid}`.
        *   Match start signal (`started=true`) written by host to `/games/{id}/started`.
        *   **Match Settings by Host:** `matchDurationMs` and `mazeComplexity` written to `/games/{id}/`.
        *   Game node removal via `leaveGame()`.

### 2.7 User Interface Description

*   **`activity_home.xml` (`HomeActivity`):**
    *   Displays the game title "InkRollers".
    *   Features a central "Play" button.
    *   Tapping "Play" reveals a submenu containing:
        *   "Host New Game" button: **Tapping this now opens a Match Settings dialog sequence (Time Limit choice, then Maze Complexity choice) before proceeding.**
        *   An `EditText` field to enter a 6-character Game ID (leaving this field blank and tapping 'Join Game' will attempt to find a random available game).
        *   "Join Game" button.
*   **`activity_main.xml` (`MainActivity`):**
    *   Dominated by the `GameView` which takes up most of the screen space for rendering the game world.
    *   Overlays HUD elements:
        *   `TimerHudView` (Top Right): Displays remaining match time (MM:SS).
        *   `InkHudView` (Top Left): Shows the local player's ink level bar and current mode text.
        *   `CoverageHudView` (Below Ink HUD): Displays coverage percentage bars for active players.
    *   A "P1 Toggle" button (Bottom Right) allows the local player to switch between PAINT and FILL modes. (P2 button is present but hidden/unused).
    *   Standard `AlertDialog`s are used for "Waiting...", "Countdown", and "Rematch" prompts.

### 2.8 Data Flow & Interaction Diagram

```
// Conceptual Diagram - Simplified

// ---- Pre-Match Flow ----
HomeActivity --(Tap "Host New Game")--> HomeActivity: showMatchSettingsDialog()
  |                                         | User selects Time Limit & Maze Complexity
  |                                         |
HomeActivity --(Intent w/ Mode=HOST, Time, Complexity)--> MainActivity
  |                                      | onCreate() -> new MultiplayerManager()
  |                                      | signInAnonymouslyAndProceed() -> handleIntentExtras() -> Stores Time, Complexity
  |                                      |   -> MultiplayerManager.hostGame(initialState, durationMs, complexity) 
  |                                      |     -> Firebase write (players/player0, mazeSeed, matchDurationMs, mazeComplexity)
  |                                      |   -> showWaitingForPlayersDialog()
  |
HomeActivity --(Intent w/ Mode=JOIN, ID)--> MainActivity
                                             | onCreate() -> new MultiplayerManager()
                                             | signInAnonymouslyAndProceed() -> handleIntentExtras()
                                             |   -> MultiplayerManager.joinGame(ID, initialState) 
                                             |     -> Firebase read (seed, matchDurationMs, mazeComplexity) -> Returns GameSettings
                                             |     -> MainActivity stores received Time, Complexity
                                             |     -> Firebase write (players/playerN)
                                             |   -> showWaitingForHostDialog()

HomeActivity --(Intent w/ Mode=JOIN, ID=null)--> MainActivity
                                                       | signInAnonymouslyAndProceed() -> handleIntentExtras()
                                                       |   -> MultiplayerManager.joinGame(null, initialState)
                                                       |     -> MultiplayerManager.findRandomAvailableGame()
                                                       |       -> (if game found, proceeds like specific join with found ID)
                                                       |   -> showWaitingForHostDialog() (or "no games found" if applicable)

Firebase --(Player Added)--> MultiplayerManager --> onPlayerCountChanged --> MainActivity (Host)
  |                                      | (Flow to countdown largely the same)
  |
Firebase --(started=true)--> MultiplayerManager --> onMatchStartRequested --> MainActivity (Join)
                                             | (Flow to countdown largely the same)

actuallyStartMatch() [Both Host & Join]
  -> gameView.initGame(mazeComplexity) // Creates new Level (with chosen complexity), new Thread
  -> gameView.setLocalPlayerId()
  -> gameView.startGameMode(GameMode.COVERAGE, matchDurationMs) // Uses chosen duration
  -> gameView.startGameLoop()


// ---- Gameplay Loop ----
User Touch -> GameView.onTouchEvent() -> VirtualJoystick.onMove()
  |
  -> GameView.update(deltaTime) // deltaTime passed from GameThread
     | -> Player.move(..., deltaTime) // Movement uses deltaTime
     |    | (Collision, Paint/Refill logic as before)
     | -> (Multiplayer state/paint sync as before)
     | -> IF isMatchReady: GameModeManager.update() // Tracks time based on initial duration
     |      -> (Coverage, HUD updates as before)
     -> GameView.draw()

// ---- Match End / Rematch Flow ----
GameView.update() --(GameModeManager.isFinished() == true)-->
  | (Rematch dialog flow as before)
  |
Firebase --(All Answers In)--> MultiplayerManager --> onRematchDecision(allYes)
                                       --> MainActivity
                                           | -> IF allYes: restartMatch()
                                           |      | (Clear Firebase state, local GameView surface)
                                           |      | -> gameView.initGame(mazeComplexity) // Re-init with same complexity
                                           |      | -> (Setup local player, rematch listener)
                                           |      | -> Handler.postDelayed -> gameView.startGameMode(GameMode.COVERAGE, matchDurationMs) -> gameView.startGameLoop()
                                           |
                                           | -> IF !allYes: finish() OR onDestroy()

```

---

## 3. Implementation Plan

### 3.1 Milestone Tracker

| Phase                 | Status       | Deliverables                                                                                                                                                              | Notes                                                                          |
| :-------------------- | :----------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | :----------------------------------------------------------------------------- |
| **M‑0 Project Setup** | ✅ Done      | Gradle project, AndroidX enabled, base packages.                                                                                                                        |                                                                                |
| **M‑1 Core Loop v1**  | ✅ Done      | `GameView`, `GameThread`, placeholder `Player`.                                                                                                                         |                                                                                |
| **M‑2 Paint v2**      | ✅ Done      | `PaintSurface`, drag painting, live bitmap render.                                                                                                                        | Control changed in M-7.                                                        |
| **M‑3 Mode UI v3**    | ✅ Done      | XML layout, toggle buttons.                                                                                                                                               | Two-finger gesture removed.                                                    |
| **M‑4 Refill v4**     | ✅ Done      | Implement ink depletion + refill when `mode==FILL`.                                                                                                                       | No longer uses erase.                                                          |
| **M‑5 Ink HUD v5**    | ✅ Done      | Overlay showing ink meter & current mode (`InkHudView`).                                                                                                                  |                                                                                |
| **M‑6 Multi-roller**  | ✅ Done      | Support two independent `Player` objects via multi‑touch (removed).                                                                                                       | Controls changed in M-7.                                                       |
| **M‑7 Level System**  | ✅ Done      | `Level` interface, `MazeLevel` (Recursive Backtracker), collision walls, `VirtualJoystick` controls.                                                                      | `RoomSequenceLevel` deferred.                                                  |
| **M‑8 Networking MVP**| ✅ Done      | `MultiplayerManager` with Firebase lobby, state sync (`PlayerState`), synchronized maze. **Orientation sync, paint normalization added.**                                | Initial sync issues resolved in M9.                                            |
| **M‑9 Sync Fixes**    | ✅ Done      | Normalized coordinate sync for positions & paint; deferred maze init; `push()`-based paint events; correct read/cast of normalized coords.                                | Sync issues resolved.                                                          |
| **M‑10 Coverage Mode**| ✅ Done      | `CoverageCalculator`, `CoverageHudView`, `GameModeManager`, integration.                                                                                                | 60-second Coverage matches supported. Winner logic fixed.                      |
| **M‑11 PreMatch/Rematch** | ✅ Done | Added Waiting/Countdown flow. Fixed rematch loop and state reset issues. Added Firebase game cleanup. Added `TimerHudView`. Resolved initialization/rendering bugs. Ensured frame-rate independent player movement (delta timing). Corrected `TimerHudView` position. Solidified maze wall corners for collision. | Ensures smooth multiplayer start/restart/rendering and consistent gameplay. Includes fix for duplicate game creation on host and implementation of joining random available games. Refined Firebase connection check logic. |
| **M‑11.5 Match Customization** | ✅ Done | Added Match Settings dialog (Time Limit, Maze Complexity) for hosts. Maze complexity adjusts cell density. Settings synced via Firebase for joining players.        | Provides more replayability and control.                                       |
| **M‑12 Zones Mode**   | ☐ **Next** | Define zones, calculate per-zone control, zone HUD, integrate into `GameModeManager`.                                                                                   | **Next major feature.**                                                        |
| **M‑13 Audio / FX**   | ☐          | SoundPool SFX, basic particle splats.                                                                                                                                     |                                                                                |
| **M‑14 Polish/Release**| ☐          | Icons, onboarding, Google Play bundle, privacy policy.                                                                                                                    |                                                                                |

### 3.2 Detailed Upcoming Tasks

1.  **(Next) Zones Mode**
    *   Define zone regions in `Level` (extend interface with `getZones(): List<RectF>` in normalized maze coords). Add implementation in `MazeLevel`.
    *   Implement `ZoneOwnershipCalculator`:
        *   Sample pixels within each zone RectF, skip walls, tally color counts.
        *   Determine majority owner for each zone.
        *   Provide unit tests.
    *   Create `ZoneHudView`:
        *   Display showing each zone's current owner color (mini-map or list).
        *   API: `updateZones(Map<Int, Int>)` mapping zone index → color.
    *   Extend `GameModeManager` to support `ZONES` mode.
    *   Integrate into `GameView.update()`: If mode==ZONES, run `ZoneOwnershipCalculator`, push to `ZoneHudView`.
    *   Display final zone control overlay at match end.
    *   Update `MainActivity` / `HomeActivity` to allow selecting `ZONES` mode.

2.  **Performance & Memory**
    *   Cap frame rate in `GameThread` (e.g., `Thread.sleep(16)` for ~60 FPS).
    *   Optimize sampling resolution in `CoverageCalculator`.
    *   Analyze bitmap memory usage (`PaintSurface`).

3.  **(Deferred) RoomSequenceLevel / LevelManager**
    *   Implement a `RoomSequenceLevel` type.
    *   Create `LevelManager` to sequence levels.

4.  **QA & Release**
    *   Test on target devices API 26–34.
    *   Add tests (Robolectric, Espresso).
    *   Iterate on UX, graphics, audio, onboarding.
    *   Prepare Play Store artifacts.

---

## 4. Risks & Mitigations

| Risk                             | Impact   | Mitigation                                                                                                        |
| :------------------------------- | :------- | :---------------------------------------------------------------------------------------------------------------- |
| Real-time sync drift             | Low      | Fixed with normalized coords & state sync. Latency inherent.                                                      |
| Coverage sampling performance    | Moderate | Adjust `sampleStep`, run less frequently, or use worker thread if needed.                                           |
| Memory growth from `PaintSurface`| High     | Monitor usage. Consider tiling or optimized bitmap handling if it becomes an issue on lower-end devices.          |
| Firebase cost                    | Medium   | Minimize write frequency; batch updates (already using `updateChildren` for state reset). Automatic cleanup helps. |
| Zone calculation performance     | Medium   | Optimize sampling in `ZoneOwnershipCalculator`, similar to coverage.                                              |

---

## 5. Appendix – File Map

```text
app/
 └─ src/main/
    ├─ java/com/spiritwisestudios/inkrollers/
    │   ├─ HomeActivity.kt
    │   ├─ MainActivity.kt
    │   ├─ GameView.kt
    │   ├─ GameThread (inner class in GameView.kt)
    │   ├─ Player.kt
    │   ├─ PlayerState.kt
    │   ├─ PaintSurface.kt
    │   ├─ Level.kt
    │   ├─ MazeLevel.kt
    │   ├─ VirtualJoystick.kt
    │   ├─ InkHudView.kt
    │   ├─ MultiplayerManager.kt
    │   ├─ CoverageCalculator.kt
    │   ├─ CoverageHudView.kt
    │   ├─ GameModeManager.kt
    │   ├─ TimerHudView.kt
    │   └─ (future) ZoneOwnershipCalculator.kt, ZoneHudView.kt
    └─ res/
        ├─ layout/
        │   ├─ activity_main.xml
        │   └─ activity_home.xml
        └─ ... (drawable, values, mipmap, etc.)

build.gradle (Project level)
app/build.gradle (App level)
gradle/wrapper/gradle-wrapper.properties
gradle.properties
settings.gradle
AndroidManifest.xml
```

*End of document.*
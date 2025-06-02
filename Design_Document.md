# Ink  Rollers – Design Document

*(version 1.0 – May 22, 2025)*
*(Revised based on detailed design document principles and codebase review)*

---

## 0. Introduction

### 0.1 Purpose and Scope

**Purpose:** This document outlines the design for "Ink Rollers," a casual, real-time multiplayer Android game. It details the system architecture, component design, interfaces, and other considerations necessary for its development, maintenance, and future enhancements.

**Scope:** The game involves players controlling paint rollers to cover an arena floor, competing in various modes like "Coverage" and "Zones." Key features include configurable match settings (time limits, maze complexity), multiplayer interaction via Firebase Realtime Database, and player profile management. This document covers the application from initial user interaction on the home screen through gameplay, match conclusion, and rematch sequences.

### 0.2 Definitions, Acronyms, and Abbreviations

| Term         | Definition                                                                 |
| :----------- | :------------------------------------------------------------------------- |
| **RTDB**     | Realtime Database (specifically Firebase Realtime Database)                |
| **MVP**      | Minimum Viable Product                                                     |
| **HUD**      | Heads-Up Display                                                           |
| **UI**       | User Interface                                                             |
| **UX**       | User Experience                                                            |
| **SDK**      | Software Development Kit                                                   |
| **API**      | Application Programming Interface                                          |
| **AGP**      | Android Gradle Plugin                                                      |
| **BoM**      | Bill of Materials (used for Firebase dependencies)                         |
| **SFX**      | Sound Effects                                                              |
| **UML**      | Unified Modeling Language                                                  |
| **DFD**      | Data Flow Diagram                                                          |
| **ER Diagram** | Entity Relationship Diagram                                                |
| **IDE**      | Integrated Development Environment                                         |
| **FPS**      | Frames Per Second                                                          |
| **TTL**      | Time To Live (used in game cleanup logic)                                  |
| **BaaS**     | Backend as a Service                                                       |

### 0.3 Document Overview

This document is structured as follows:
*   **Section 0: Introduction** - Purpose, scope, definitions, and this overview.
*   **Section 1: System Overview** - High-level architecture and subsystem responsibilities.
*   **Section 2: Design Considerations** - Key design decisions, constraints, and dependencies.
*   **Section 3: System Architecture** - Backend architecture, components, modules, database design, and architectural patterns.
*   **Section 4: Interface Design** - User interfaces and API definitions.
*   **Section 5: Component Design** - Detailed design of components using conceptual UML models.
*   **Section 6: System Behavior** - Use cases, user stories, and task flows.
*   **Section 7: Current Code Architecture (v12 Snapshot)** - A detailed look at the existing codebase structure and components as of version 12.
*   **Section 8: Quality Requirements** - Non-functional requirements like performance, security, etc.
*   **Section 9: Test Plan** - Strategy for testing the application.
*   **Section 10: Implementation Plan & Considerations** - Technology stack, development roadmap, and deployment.
*   **Section 11: Potential Codebase Improvements** - Suggestions for enhancing the codebase.
*   **Section 12: Risks & Mitigations** - Identified risks and their mitigation strategies.
*   **Section 13: Appendices** - References, Glossary (combined with Definitions), and Revision History.

---

## 1. System Overview

### 1.1 High-Level Architecture

Ink Rollers is an Android mobile game application with a client-server architecture, where the Firebase Realtime Database acts as the Backend as a Service (BaaS). Clients (Android devices running the game) interact with Firebase for game state synchronization, matchmaking, and player data persistence.

*(Placeholder for a High-Level Context Diagram showing User -> Android App -> Firebase RTDB interactions)*

**Key Architectural Components:**
*   **Game Client (Android App):** Handles user interface, game logic, rendering, local input, and communication with Firebase.
*   **Firebase Realtime Database:** Stores and synchronizes shared game state (player positions, paint data, match status, game settings), player profiles, and facilitates matchmaking.
*   **Firebase Authentication:** Used for anonymous user sign-in to uniquely identify users for profile and game association.
*   **Firebase App Check:** Provides an additional layer of security by verifying that requests to Firebase services originate from authentic app instances.

### 1.2 Subsystems and Responsibilities

*   **Game Logic Subsystem:**
    *   Manages core gameplay mechanics: player movement, painting, ink management, collision detection.
    *   Handles different game modes (Coverage, Zones) and their specific rules.
    *   Controls the game loop and updates game state.
    *   *Key Classes:* `GameView`, `Player`, `Level` (and implementations like `MazeLevel`), `GameModeManager`, `PaintSurface`.
*   **Rendering Subsystem:**
    *   Draws the game world, players, paint, maze, and HUD elements on the screen.
    *   *Key Classes:* `GameView`, `PaintSurface`, `MazeLevel`, various HUD `View` classes.
*   **Input Subsystem:**
    *   Processes user touch input for controlling the player via the virtual joystick and interacting with UI buttons.
    *   *Key Classes:* `GameView` (for joystick), `HomeActivity`, `MainActivity` (for buttons).
*   **Multiplayer & Networking Subsystem:**
    *   Manages all communication with Firebase RTDB.
    *   Handles game hosting, joining, player state synchronization, paint action broadcasting, and rematch logic.
    *   *Key Classes:* `MultiplayerManager`, `PlayerState`.
*   **UI & HUD Subsystem:**
    *   Displays game information (ink levels, mode, timer, coverage, zone ownership) and game controls.
    *   Manages navigation between screens (Home, Game).
    *   *Key Classes:* `HomeActivity`, `MainActivity`, `InkHudView`, `CoverageHudView`, `TimerHudView`, `ZoneHudView`, `ProfileFragment`.
*   **Player Profile & Persistence Subsystem:**
    *   Manages player profile data (name, colors, stats, friends).
    *   Handles saving and loading profiles to/from Firebase.
    *   *Key Classes:* `ProfileFragment`, `ProfileRepository`, `PlayerProfile`.

---

## 2. Design Considerations

### 2.1 Key Design Decisions and Rationale

*   **Backend Technology (Firebase Realtime Database):**
    *   **Decision:** Use Firebase RTDB for backend services.
    *   **Rationale:** Rapid development, real-time data synchronization suitable for multiplayer games, easy scalability for a casual game, built-in authentication, and generous free tier. Reduces the need for custom backend server development.
*   **Multiplayer Synchronization Strategy (Normalized Coordinates):**
    *   **Decision:** Synchronize player positions and paint actions using normalized maze coordinates (0.0-1.0 range).
    *   **Rationale:** Ensures consistent representation across different screen sizes and aspect ratios, simplifying cross-device gameplay logic.
*   **Game State Management (Client-Authoritative with Firebase Sync):**
    *   **Decision:** Local player actions (movement, painting) are processed immediately on the client for responsiveness. State is then synced to Firebase for other players. Firebase acts as the source of truth for shared state.
    *   **Rationale:** Balances responsiveness with consistency. Purely server-authoritative might introduce unacceptable latency for a painting game.
*   **Maze Generation (Depth-First Search with Rotational Symmetry):**
    *   **Decision:** Use a DFS-based algorithm with 180-degree rotational symmetry for maze generation, with additional path braiding.
    *   **Rationale:** Creates "perfect" mazes (initially) ensuring connectivity, while symmetry adds an element of fairness and predictability. Braiding adds complexity and replayability.
*   **Game Loop Implementation (`GameThread`):**
    *   **Decision:** A dedicated `GameThread` manages the update and draw cycle.
    *   **Rationale:** Standard practice for Android games to separate game logic and rendering from the main UI thread, preventing ANR (Application Not Responding) errors.
*   **Paint System (`PaintSurface`):**
    *   **Decision:** Use an off-screen `Bitmap` to store painted areas.
    *   **Rationale:** Efficient for drawing and querying pixel colors (for refill and coverage/zone calculations).
*   **Anonymous Authentication:**
    *   **Decision:** Use Firebase Anonymous Authentication.
    *   **Rationale:** Lowers barrier to entry for users (no need to create accounts immediately) while still providing unique UIDs for profile and game management.

### 2.2 Constraints

*   **Platform Constraint:** Android mobile devices.
*   **Technical Constraints:**
    *   Reliance on Firebase services availability and performance.
    *   Limited processing power and memory on mobile devices, especially for rendering and complex calculations (e.g., coverage).
    *   Network latency affecting real-time synchronization.
*   **User Constraints:**
    *   Targeted at casual gamers, implying simple controls and intuitive gameplay.
    *   Short match durations suitable for mobile play sessions.
*   **Business Constraints (Assumed):**
    *   Rapid development for quick market entry (supported by Firebase).
    *   Scalability to handle a growing user base.

### 2.3 External Dependencies

*   **Firebase Realtime Database:** Core for multiplayer, game state, and profile persistence.
*   **Firebase Authentication:** For user identification.
*   **Firebase App Check (Play Integrity):** For enhancing backend security.
*   **Android SDK:** The fundamental platform.
*   **AndroidX Libraries:** Standard support libraries (AppCompat, Core KTX, etc.).
*   **Kotlin Standard Library:** Primary programming language.

---

## 3. System Architecture (Backend & Core Logic)

### 3.1 Architectural Styles and Patterns

*   **Client-Server Architecture:** The Android app (client) communicates with Firebase (server/BaaS) for data storage and synchronization.
*   **Event-Driven Architecture:** Firebase RTDB updates trigger events that client listeners respond to (e.g., player state changes, new paint actions). This is particularly evident in `MultiplayerManager`.
*   **Layered Architecture (Conceptual):**
    *   **Presentation Layer:** UI (`Activities`, `Fragments`, `Views`).
    *   **Game Logic Layer:** Core game mechanics (`GameView`, `Player`, `Level`).
    *   **Data/Networking Layer:** Firebase interaction (`MultiplayerManager`, `ProfileRepository`).
*   **Observer Pattern:** Used extensively with Firebase listeners (`ValueEventListener`, `ChildEventListener`) where components (e.g., `GameView` via `MultiplayerManager`) observe changes in the database.
*   **Model-View-Controller (MVC) / Model-View-Presenter (MVP) - Loose Adaptation:**
    *   **Model:** `PlayerState`, `PlayerProfile`, game data in Firebase, `PaintSurface` bitmap.
    *   **View:** Android `Activities`, `XML layouts`, custom `View` classes (`GameView`, HUDs).
    *   **Controller/Presenter:** `MainActivity`, `HomeActivity`, parts of `GameView`, and `MultiplayerManager` mediate between UI, game logic, and data.

### 3.2 Data Flow Diagrams (Conceptual)

*(Placeholder for DFDs. These would visually represent data movement, e.g.:)*
*   *DFD for Player Joining a Game*
*   *DFD for Player Movement and Painting Action*
*   *DFD for Rematch Process*

### 3.3 Database Design (Firebase RTDB)

The Firebase RTDB has a JSON-like structure. Key nodes include:

*   **`/games/{gameId}`:** Root for each game instance.
    *   `createdAt`: Timestamp (ServerValue.TIMESTAMP)
    *   `lastActivityAt`: Timestamp (ServerValue.TIMESTAMP)
    *   `isPrivate`: Boolean
    *   `mazeSeed`: Long
    *   `matchDurationMs`: Long
    *   `mazeComplexity`: String ("LOW", "MEDIUM", "HIGH")
    *   `gameMode`: String ("COVERAGE", "ZONES")
    *   `started`: Boolean (indicates if match countdown has begun/completed)
    *   `startTime`: Long (synchronized server timestamp for match start)
    *   `playerCount`: Long (number of players at match start, used for rematch coordination)
    *   `rematchInProgress`: Boolean (flag to coordinate rematch state reset)
    *   `players/{playerId}`: Node for each player in the game.
        *   `active`: Boolean
        *   `color`: Int
        *   `ink`: Float
        *   `mazeSeed`: Long (should match game's mazeSeed)
        *   `mode`: Int (0 for PAINT, 1 for FILL)
        *   `normX`: Float (normalized X position)
        *   `normY`: Float (normalized Y position)
        *   `playerName`: String
        *   `uid`: String (Firebase Auth UID)
    *   `paint/{pushId}`: List of paint actions.
        *   `color`: Int
        *   `normalizedX`: Float
        *   `normalizedY`: Float
        *   `player`: String (playerId who painted)
        *   `timestamp`: Timestamp (ServerValue.TIMESTAMP)
    *   `rematchRequests/{playerId}`: Boolean (true if player wants rematch, false otherwise)
*   **`/profiles/{userId}`:** Root for each user's profile.
    *   `uid`: String (Firebase Auth UID)
    *   `playerName`: String
    *   `favoriteColors`: List<Int>
    *   `catchPhrase`: String
    *   `friendCode`: String (unique 6-char code)
    *   `friends`: List<String> (list of friend UIDs)
    *   `winCount`: Int
    *   `lossCount`: Int
    *   `isOnline`: Boolean

---

## 4. Interface Design

### 4.1 User Interface (UI) Design

*(Placeholder for Wireframes and Mockups. Textual descriptions are in Section 7.7)*

The UI aims for simplicity and intuitiveness, suitable for a casual game.
*   **Home Screen:** Clean, with a prominent "Play" button leading to game options. Profile access is also available.
*   **Game Screen:** Dominated by the `GameView` for action. HUD elements are overlaid non-intrusively to provide essential game information. Controls include a virtual joystick and a mode toggle button.
*   **Dialogs:** Standard Android `AlertDialogs` are used for matchmaking progress (waiting, countdown) and post-match interactions (rematch).
*   **Profile Screen:** Allows users to customize their name, preferred colors, and catchphrase, and manage a friends list.

### 4.2 API Design (Firebase Interaction)

While not a traditional REST API, interactions with Firebase RTDB constitute the application's backend API. These are primarily managed by `MultiplayerManager` and `ProfileRepository`.

**Key Firebase "Endpoints" (Paths) and Operations:**

*   **Game Creation (Host):**
    *   Path: `/games/{newGameId}`
    *   Operation: `setValue()` with initial game data (settings, host player state).
*   **Game Joining (Joiner):**
    *   Path: `/games/{gameId}/players/{newPlayerId}`
    *   Operation: `setValue()` with joiner's initial player state.
    *   Path: `/games/{gameId}`
    *   Operation: `addListenerForSingleValueEvent()` to read game settings.
*   **Player State Update:**
    *   Path: `/games/{gameId}/players/{localPlayerId}`
    *   Operation: `setValue()` or `updateChildren()` with `PlayerState` object or partial updates.
*   **Paint Action:**
    *   Path: `/games/{gameId}/paint/`
    *   Operation: `push().setValue()` with paint data (normalized coords, color, timestamp).
*   **Rematch Answer:**
    *   Path: `/games/{gameId}/rematchRequests/{localPlayerId}`
    *   Operation: `setValue()` with boolean.
*   **Profile Save/Load:**
    *   Path: `/profiles/{userId}`
    *   Operations: `setValue()` to save, `get()` to load.
*   **Friend Code Lookup:**
    *   Path: `/profiles/`
    *   Operation: `orderByChild("friendCode").equalTo(code).get()`

Data Payloads are primarily Kotlin data classes like `PlayerState` and `PlayerProfile`, which Firebase serializes to/from JSON.

---

## 5. Component Design

*(Placeholder for detailed UML Diagrams: Class Diagrams, Entity Relationship Diagrams, Activity Diagrams, Sequence Diagrams, State Diagrams)*

**Conceptual Overview (examples of what diagrams would show):**

*   **Class Diagram:** Would show key classes like `GameView`, `Player`, `MultiplayerManager`, `MazeLevel`, `PlayerState`, `PlayerProfile`, their attributes, methods, and relationships (inheritance, aggregation, composition, dependency). For instance, `GameView` *has a* `MultiplayerManager`, *contains multiple* `Player` objects, and *uses a* `Level`.
*   **Entity Relationship Diagram (for Firebase Data):** Would visually model the structure of data in Firebase, showing entities like "Game", "PlayerInGame", "PaintAction", "UserProfile", "Friendship" and their relationships.
*   **Activity Diagram (e.g., "Joining a Game"):** Would show the flow of activities from a user tapping "Join Game" to successfully entering a match, including UI interactions, Firebase calls, and state changes.
*   **Sequence Diagram (e.g., "Player Paints"):** Would illustrate the time-ordered sequence of interactions: `Player` -> `GameView` -> `MultiplayerManager` -> Firebase RTDB, and then Firebase RTDB -> other clients' `MultiplayerManager` -> `GameView` -> `PaintSurface`.
*   **State Diagram (e.g., "Game State"):** Could model the states of `MainActivity` or the overall game flow: `Initializing` -> `WaitingForPlayers` -> `Countdown` -> `GameplayActive` -> `MatchEnded` -> `RematchPending`.

---

## 6. System Behavior

### 6.1 Use Cases (Examples)

*   **UC-001: Host a New Game**
    *   **Actor:** User (Player 1)
    *   **Description:** User initiates and configures a new game session that another player can join.
    *   **Preconditions:** User is on the Home Screen.
    *   **Flow:**
        1.  User taps "Play" button.
        2.  User taps "Host New Game" button.
        3.  System presents dialog for Time Limit selection. User selects.
        4.  System presents dialog for Maze Complexity selection. User selects.
        5.  System presents dialog for Game Mode selection. User selects.
        6.  System presents dialog for Match Type (Public/Private). User selects.
        7.  User confirms.
        8.  System launches `MainActivity`, authenticates user anonymously.
        9.  `MultiplayerManager` creates a new game node in Firebase with a unique Game ID, selected settings, and host player data.
        10. System displays "Waiting for other players..." dialog.
    *   **Postconditions:** Game is created in Firebase. Host is waiting for another player.
*   **UC-002: Join an Existing Game (with ID)**
    *   **Actor:** User (Player 2)
    *   **Description:** User joins a game session hosted by another player using a known Game ID.
    *   **Preconditions:** User is on the Home Screen. Game with the specified ID exists and has space.
    *   **Flow:**
        1.  User taps "Play" button.
        2.  User enters a 6-character Game ID.
        3.  User taps "Join Game" button.
        4.  System launches `MainActivity`, authenticates user anonymously.
        5.  `MultiplayerManager` attempts to join the game in Firebase.
        6.  If successful, `MultiplayerManager` adds joiner's player data to the game node and retrieves game settings.
        7.  System displays "Waiting for host to start..." dialog.
    *   **Postconditions:** Joiner is added to the game in Firebase and waiting for the host to start.
*   **UC-003: Play a Match (Coverage Mode)**
    *   (Details flow of painting, ink management, timer countdown, HUD updates, and end-of-match coverage calculation)
*   **UC-004: Request a Rematch**
    *   (Details flow of match end, rematch dialog, sending answer to Firebase, and handling responses)
*   **UC-005: Manage Player Profile**
    *   (Details flow of accessing profile, editing name/colors, adding/removing friends)

### 6.2 User Stories (Examples)

*   **As a player, I want to host a new game with customizable settings (time, complexity, mode, privacy) so I can play the game variant I prefer.**
*   **As a player, I want to easily join a game hosted by my friend using a Game ID so we can play together.**
*   **As a player, I want to be able to join a random public game quickly so I can play even if I don't have a specific game to join.**
*   **As a player, I want to see my ink level and current mode (paint/fill) clearly so I can manage my resources effectively.**
*   **As a player, I want to see the remaining match time so I know how long I have left.**
*   **As a player in Coverage mode, I want to see the current paint coverage percentages for myself and my opponent so I know who is winning.**
*   **As a player in Zones mode, I want to see which zones are controlled by whom so I can strategize.**
*   **As a player, I want the option to play again with the same opponent immediately after a match finishes so we can have a rematch easily.**
*   **As a player, I want to customize my player name and preferred roller colors so I can personalize my appearance.**

### 6.3 Step-by-Step Task Descriptions

*(This is largely covered by "Section 7.5 System States & Runtime Flow" which provides a narrative walkthrough. More specific tasks could be detailed here if needed.)*

---

## 7. Current Code Architecture (v12 Snapshot)

*(This section retains the original detailed breakdown from the provided document, as it's a good snapshot of the codebase. Minor adjustments for clarity or consistency with other sections might be made.)*

### 7.1 Package & Build
*   **Namespace:** `com.spiritwisestudios.inkrollers` (Gradle `namespace` in *app/build.gradle*).
*   **AndroidX Enabled:** via `gradle.properties` (`android.useAndroidX=true`, `android.enableJetifier=true`).
*   **SDK Versions:** Minimum SDK 26, compile/target SDK 34.
*   **Build Tools:** Kotlin 1.9.0, Android Gradle Plugin (AGP) 8.9.2. (Note: `app/build.gradle` shows AGP `com.android.application` but version is not explicitly there, usually tied to Android Studio version. Kotlin version is explicit.)
*   **Firebase:** Dependencies via BoM (Platform `33.1.2`), using `firebase-database-ktx`, `firebase-auth-ktx`, `firebase-appcheck-playintegrity`. Google Services plugin `4.4.2` applied.
*   **Screen Orientation:** Primarily landscape (`AndroidManifest.xml`).

### 7.2 Class-Level Components

| File                      | Responsibility                                                                                                                                                              | Key Methods / Notes                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| :------------------------ | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`HomeActivity.kt`**     | App entry point (Launcher). UI for Play -> Host/Join. Host presents Match Settings dialog (Time, Complexity, Game Mode, Privacy). Passes settings to `MainActivity`.           | `onCreate()`, `showMatchSettingsDialog()`, `showComplexityDialog()`, `showGameModeDialog()`, `showMatchTypeDialog()`, `startGameActivity()`. Defines mode/complexity/game mode constants. Initializes Firebase App Check. Handles Profile button. |
| **`MainActivity.kt`**     | Manages game screen. Handles profile loading, Firebase auth, game initialization based on Intent extras (mode, settings). Sets up `GameView` and `MultiplayerManager`. Coordinates pre-match (waiting, countdown) and post-match (rematch) flows. | `onCreate()`, `signInAnonymouslyAndProceed()`, `handleIntentExtras()`. `actuallyStartMatch()` calls `gameView.startGameMode()`. Handles `onMatchEnd`, `onRematchDecision`, `onRematchStartSignal`. `restartMatch()` for rematches. |
| **`GameView.kt`**         | Custom `SurfaceView` for game loop and rendering. Manages game objects (`Player`, `Level`), input (`VirtualJoystick`), multiplayer display. Implements `MultiplayerManager.RemoteUpdateListener`. Coordinates with `GameModeManager`. Handles mode-specific logic (Coverage/Zones), HUD updates. | `initGame()`, `startGameMode()`, `update(deltaTime)`, `draw()`, `finishMatch()`. `onPlayerStateChanged()`, `onPaintAction()`. Posts UI updates for HUDs to main thread. `PaintSurface` management. |
| **`GameThread`** (inner in `GameView.kt`)  | `Thread` subclass. Runs `GameView.update(deltaTime)` + `GameView.draw()` loop. Calculates `deltaTime`. New instance per match.                                    | `run()`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| **`Player.kt`**           | Represents paint roller avatar. Tracks position, mode (paint/fill), ink, color, name. Moves via `move()`, checks `Level` collision, paints onto `PaintSurface`. Sends paint actions with normalized maze coordinates. | `move()`, `toggleMode()`, `getInkPercent()`, `draw()`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| **`PlayerState.kt`**      | Data class for player state synced via Firebase (normalized position, color, mode, ink, active, mazeSeed, playerName, uid). Has no-arg constructor for Firebase.               | Defines player data structure for network sync.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| **`PaintSurface.kt`**     | Off-screen `Bitmap` and `Canvas` for painted pixels. Provides `getBitmap()` for direct access and `getBitmapCopy()` for persistence. Method `clear()` erases paint.          | `paintAt()`, `getPixelColor()`, `drawTo()`, `clear()`, `getBitmap()`, `getBitmapCopy()`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| **`Level.kt`**            | Interface for game levels. Defines `update()`, `draw()`, `checkCollision()`, `getPlayerStartPosition()`, `calculateCoverage()`, `getZones()`.                                | Abstract contract for level implementations.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| **`MazeLevel.kt`**        | Implements `Level`. Generates mazes (DFS with symmetry, braiding) with varying complexity. Implements `getZones()` (2x3 grid). Handles scaling/offsetting, coordinate conversion (screen to maze, maze to screen). | `generateMaze()`, `buildWallRects()`, `checkCollision()`, `getPlayerStartPosition()`, `screenToMazeCoord()`, `mazeToScreenCoord()`, `getZones()`. Calculates cell dimensions based on complexity and screen orientation. |
| **`VirtualJoystick.kt`**  | Manages on-screen virtual joystick logic and rendering. Provides normalized direction and magnitude.                                                                             | `onDown()`, `onMove()`, `onUp()`, `draw()`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| **`InkHudView.kt`**       | Custom `View` for local player's ink level and mode display.                                                                                                                      | `updateHud()`, `onDraw()`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| **`CoverageHudView.kt`**  | Custom `View` for coverage percentage bars in Coverage mode. Visibility managed by `GameView`.                                                                                    | `updateCoverage()`, `onDraw()`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| **`TimerHudView.kt`**     | Custom `View` for remaining match time display (MM:SS).                                                                                                                           | `updateTime()`, `onDraw()`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| **`ZoneHudView.kt`**      | Custom `View` to display zone ownership as a mini-map grid in Zones mode. Visibility managed by `GameView`.                                                                       | `updateZones()`, `onDraw()`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| **`MultiplayerManager.kt`** | Handles all Firebase RTDB interactions: host/join game, player state sync, paint sync, rematch logic, match start signal, game settings sync, stale game cleanup. Implements `RemoteUpdateListener` callbacks for `GameView`. | `hostGame()`, `joinGame()`, `findRandomAvailableGame()`, `updateLocalPlayerState()`, `sendPaintAction()`, `setupFirebaseListeners()`, `leaveGame()`, `sendMatchStart()`, `sendRematchAnswer()`, `setupRematchListener()`, `resetAllPlayerStatesFirebase()`, `performStaleGameCleanup()`. |
| **`CoverageCalculator.kt`** | Static utility object to calculate coverage fraction per color on a `PaintSurface` within a `MazeLevel`.                                                                      | `calculate(level, paintSurface, sampleStep)`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| **`GameModeManager.kt`**  | Encapsulates match timing and current game mode logic. Tracks `startTime`, `durationMs`, and if the match is `finished`.                                                        | `start()`, `update()`, `isFinished()`, `timeRemainingMs()`. `GameMode` enum (`COVERAGE`, `ZONES`).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| **`ZoneOwnershipCalculator.kt`** | Static utility object to determine zone ownership by sampling pixels within predefined zones on the `PaintSurface`, skipping walls, and identifying the majority owner. | `calculateZoneOwnership(level, paintSurface, sampleStep)`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| **`PlayerProfile.kt`**    | Data class for player profile (UID, name, colors, phrase, friend code, friends, stats, online status). Includes `PlayerColorPalette`.                                           | Defines user profile structure for Firebase.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| **`ProfileRepository.kt`**| Object for saving/loading `PlayerProfile` data to/from Firebase. Handles friend code uniqueness checks and finding profiles. Manages user online status.                         | `savePlayerProfile()`, `loadPlayerProfile()`, `findProfileByFriendCode()`, `isFriendCodeUnique()`, `setUserOnlineStatus()`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| **`ProfileFragment.kt`**  | `Fragment` for displaying and editing player profile. Interacts with `ProfileRepository`. Manages friend list UI with `FriendAdapter`.                                            | `onViewCreated()`, `populateProfile()`, `saveProfile()`, `addFriendByCode()`, `generateUniqueFriendCodeAndCreateProfile()`, `setupColorPickers()`.                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| **`FriendAdapter.kt`**    | `RecyclerView.Adapter` for displaying the list of friends in `ProfileFragment`.                                                                                                   | `onCreateViewHolder()`, `onBindViewHolder()`. `FriendDisplay` data class.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |

### 7.3 Abstract Interfaces & Inheritance
*   **`Level` Interface:** Defines core contract (`update`, `draw`, `checkCollision`, `getPlayerStartPosition`, `calculateCoverage`, `getZones`).
    *   **`MazeLevel` Class:** Implements `Level`.
*   **Android View System:**
    *   `GameView` extends `SurfaceView`.
    *   `InkHudView`, `CoverageHudView`, `TimerHudView`, `ZoneHudView` extend `View`.
*   **Listener Interfaces:**
    *   `GameView` implements `MultiplayerManager.RemoteUpdateListener`.
    *   Various Firebase listeners (`ValueEventListener`, `ChildEventListener`) used in `MultiplayerManager`.
*   **Android Activity/Fragment Lifecycle:**
    *   `HomeActivity`, `MainActivity` extend `AppCompatActivity`.
    *   `ProfileFragment` extends `Fragment`.

### 7.4 Key Data Structures
*   **`ConcurrentHashMap<String, Player>`:** In `GameView` for active players.
*   **`ConcurrentHashMap<String, PlayerState>`:** In `GameView` (`pendingPlayerStates`) for caching early player updates.
*   **`ConcurrentHashMap<String, VirtualJoystick>`:** In `GameView` for joysticks (currently only local player).
*   **`PlayerState` (Data Class):** For Firebase sync.
*   **`GameSettings` (Data Class):** In `MultiplayerManager` for game config (duration, complexity, gameMode).
*   **`PlayerProfile` (Data Class):** For user profile data.
*   **`Map<Int, Float>`:** For coverage results.
*   **`Map<Int, Int?>`:** For zone ownership results (Zone Index -> Owner Color or null).
*   **`List<RectF>`:** In `MazeLevel` for wall bounding boxes; in `Level` interface for zones.
*   **`Bitmap` / `Canvas`:** In `PaintSurface`.

### 7.5 System States & Runtime Flow

(This largely matches the detailed flow provided in the original "System States & Runtime Flow" section of the document. It accurately describes the transitions from Home Screen -> Host/Join -> Waiting -> Countdown -> Gameplay -> Match End/Rematch, including how game settings and modes are handled.)

### 7.6 System Inputs & Outputs

(This largely matches the detailed inputs/outputs provided in the original "System Inputs & Outputs" section, covering user touch, Firebase events as inputs, and screen display, Firebase writes as outputs. The handling of normalized coordinates and game settings is correctly noted.)

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
        *   Synchronized match start time (`startTime`) written by host to `/games/{id}/startTime` and read by all clients before starting the match timer.
        *   Game settings including `matchDurationMs`, `mazeComplexity`, **and `gameMode`** read by joining clients.
*   **Outputs:**
    *   **Screen Display:**
        *   Rendered game state via `GameView` (maze, paint, players, joystick).
        *   HUD overlays via `InkHudView`, `CoverageHudView`, `TimerHudView`, **`ZoneHudView`**.
        *   UI elements in `HomeActivity` and `MainActivity` (buttons, text, dialogs).
    *   **Firebase Realtime Database Writes:**
        *   Local player's full state (`PlayerState`) updates during gameplay to `/games/{id}/players/{pid}`.
        *   Local player's *partial* state (initial `normX`, `normY`) update immediately after local initialization in `GameView` via `updateChildren`.
        *   Paint actions (including normalized coordinates) pushed to `/games/{id}/paint/`.
        *   Rematch answers written to `/games/{id}/rematchRequests/{pid}`.
        *   Match start signal (`started=true`) written by host to `/games/{id}/started`.
        *   **Match Settings by Host:** `matchDurationMs`, `mazeComplexity`, **and `gameMode`** written to `/games/{id}/`.
        *   Game node removal via `leaveGame()`.

(This largely matches the UI descriptions from the original document for `activity_home.xml` and `activity_main.xml`, including the dialogs and HUD elements. The recent changes for Zones mode HUD and Timer HUD sizing are correctly noted.)

### 7.8 Data Flow & Interaction Diagram (Conceptual)

(The original textual diagram provides a good high-level conceptual flow. Formal DFDs would be more detailed but this serves as a starting point.)

---


## 8. Quality Requirements

### 8.1 Performance
*   **Target Frame Rate:** Aim for a consistent 30-60 FPS during gameplay on mid-range target devices.
*   **Response Time:** User input (joystick, button taps) should feel instantaneous (e.g., <100ms UI response).
*   **Network Latency:** While variable, the game should gracefully handle typical mobile network latencies. Local actions are immediate. Remote player updates should appear smoothly.
*   **Scalability (Firebase):** Firebase RTDB is designed for scalability. The data structure should be optimized to support a reasonable number of concurrent games and players without excessive costs or performance degradation. Stale game cleanup is implemented.
*   **Coverage/Zone Calculation:** These calculations should not cause noticeable frame drops. `sampleStep` parameters are used for tuning.

### 8.2 Security
*   **Firebase App Check:** Implemented in `HomeActivity` using Play Integrity to help ensure requests to Firebase originate from authentic app instances.
*   **Firebase Database Rules:**
    *   *(To be defined/verified)* Rules should be configured to:
        *   Allow players to write only to their own data within a game (`/games/{gameId}/players/{playerId}`).
        *   Allow hosts to write to game-level settings.
        *   Validate data types and structures where possible.
        *   Secure profile data (`/profiles/{userId}` should only be writable by the owner).
*   **Input Validation:** Basic client-side validation for inputs like Game ID length. Further server-side validation via Firebase Rules is recommended.
*   **Data Privacy:** Player UIDs are used. No other PII is explicitly collected beyond user-chosen player names and catchphrases. A privacy policy will be required for store release.

### 8.3 Usability
*   **Learnability:** Game controls and objectives should be easy to understand for new players.
*   **Efficiency:** Players should be able to perform common actions (join game, toggle mode) quickly.
*   **User Feedback:** Clear visual feedback for actions (painting, mode changes, button presses, errors).
*   **Accessibility:** *(Consideration for future)* Basic accessibility features (e.g., adjustable text sizes, color contrast options if complex palettes are introduced).

### 8.4 Reliability
*   **Stability:** The application should not crash frequently. Robust error handling for network issues and unexpected data from Firebase.
*   **Recoverability:**
    *   If disconnected from Firebase, attempt to reconnect. State might be lost or stale if reconnection is slow.
    *   `PaintSurface` bitmap is saved/restored across `SurfaceView` destruction/recreation (e.g., app backgrounding).
*   **Data Integrity:** Firebase provides data consistency. Client-side logic should correctly interpret and apply synchronized state.

### 8.5 Maintainability
*   **Modularity:** Code is organized into classes with specific responsibilities. Large classes like `GameView` and `MultiplayerManager` could be candidates for further refactoring if complexity increases significantly.
*   **Readability:** Code should be well-formatted with clear naming conventions. Kotlin's conciseness helps. Comments for non-trivial logic.
*   **Testability:** Design components to be testable. `ProfileRepository` is an object, which is simple. `MultiplayerManager` might be harder to unit test without mocking Firebase.
*   **Configurability:** Game settings (duration, complexity, mode) are configurable.

---

## 9. Test Plan

### 9.1 Testing Strategy
A multi-layered testing approach will be used:
*   **Unit Tests:** For individual classes and methods, especially utility classes (`CoverageCalculator`, `ZoneOwnershipCalculator`), data models (`PlayerState`, `PlayerProfile`), and pure logic components.
*   **Integration Tests:** For interactions between components, e.g., `GameView` with `Player` and `Level`, or `MainActivity` with `MultiplayerManager` (mocking Firebase interactions).
*   **UI Tests (Espresso):** For testing user flows, UI element interactions, and visual output on `Activities` and `Fragments`. A key test suite, `GameFlowIntegrationTest.kt`, focuses on end-to-end testing of core game hosting and joining flows, which has undergone significant stabilization to ensure reliability.
*   **Manual Testing:** For end-to-end gameplay scenarios, multiplayer interactions across devices, and exploratory testing.

### 9.2 Test Environment
*   Local JVM for unit tests.
*   Android Emulators and physical devices (various API levels and screen sizes) for integration and UI tests.
*   Firebase Test Lab could be considered for testing on a wider range of virtual devices.

### 9.3 Key Scenarios to Test

*   **UC-001: Host a New Game (All setting combinations)**
    *   **Pass Criteria:** Game created in Firebase with correct ID and settings. Host player appears correctly. "Waiting" dialog shown. UI remains stable.
*   **UC-002: Join an Existing Game (Specific ID, Random Public)**
    *   **Pass Criteria:** Joiner successfully added to game. Game settings correctly received. "Waiting" dialog shown. Error handling for full/invalid/private games. UI remains stable.
*   **Join Random Game (No Games Available):**
    *   **Pass Criteria:** Application handles the scenario gracefully (e.g., stays on the home screen, shows an appropriate message, or navigates to a fallback screen) without crashing or entering an unstable UI state.
*   **Gameplay (Coverage & Zones Mode):**
    *   Player movement, painting, ink refill.
    *   Collision detection.
    *   Correct HUD updates (ink, timer, coverage/zones).
    *   Real-time synchronization of player movement and paint between clients.
    *   Correct win condition evaluation for each mode.
*   **Rematch Flow:**
    *   Both players select "Yes" -> Rematch starts correctly, state is reset.
    *   One player selects "No" -> Game ends, users return to appropriate screen.
*   **Profile Management:**
    *   Create, load, save profile.
    *   Add/remove friends.
    *   Color selection validation.
*   **Network Interruption Handling:**
    *   Temporary disconnection and reconnection.
    *   App backgrounding and returning.
*   **Stale Game Cleanup:**
    *   Verify that inactive/old games are eventually removed from Firebase.
*   **UI Test Suite Execution:**
    *   **Pass Criteria:** The `GameFlowIntegrationTest` suite runs to completion without failures, demonstrating stability in core user flows under test conditions.

### 9.4 Existing Tests
*   `PlayerProfileTest.kt`: Unit tests for `PlayerProfile.isValidColorSelection()`.
*   `GameFlowIntegrationTest.kt`: Espresso UI tests validating core game setup flows, including hosting a game, joining by ID, and attempting to join a random game. This suite has been specifically refactored for stability and reliability.
*   `GameFlowIntegrationTest.kt`: Espresso UI tests validating core game setup flows, including hosting a game, joining by ID, and attempting to join a random game. This suite has been specifically refactored for stability and reliability.
*   `PlayerTest.kt`: Unit tests for the `Player` class, covering:
    *   Mode toggling (paint/fill).
    *   Ink management: decrease on paint, increase on fill (correct color vs. different color), min/max ink limits, and ink percentage calculation.
    *   Basic movement: position updates, coercion within surface boundaries, and no movement on zero magnitude/delta time.
    *   Collision-based movement: handling of no collision, full collision, and sliding scenarios (X-axis and Y-axis) based on mocked `Level` interactions.
---

## 10. Implementation Plan & Considerations

### 10.1 Technology Stack
*   **Language:** Kotlin
*   **Platform:** Android (Min SDK 26, Target SDK 34)
*   **Backend:** Firebase (Realtime Database, Authentication, App Check)
*   **Build System:** Gradle
*   **IDE:** Android Studio

### 10.2 Implementation Roadmap (Milestone Tracker)

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
| **M‑11.6 Profile Integration & Init Fixes** | ✅ Done | Player names and colors now loaded from profile and used in matches. Game initialization fixed to always set up level and player at match start. Save button logic and friend code generation improved. | Includes bug fixes for game loop/thread initialization and profile save button. |
| **M‑12 Zones Mode**   | ✅ **Done** | Defined zones in `Level`/`MazeLevel`. Implemented `ZoneOwnershipCalculator`. Created `ZoneHudView`. Integrated into `GameModeManager`, `GameView`, `MainActivity`, `HomeActivity`. Added game mode selection to host settings and Firebase sync. Addressed performance issues and paint persistence. HUD positioning refined. | New game mode fully integrated. |
| **M‑13 Audio / FX**   | ☐          | SoundPool SFX, basic particle splats.                                                                                                                                     |                                                                                |
| **M‑14 Polish/Release**| ☐          | Icons, onboarding, Google Play bundle, privacy policy.                                                                                                                    |                                                                                |

### 10.3 Detailed Upcoming Tasks (Post M-12)
1.  **Performance & Memory Optimization:**
    *   Implement frame rate capping in `GameThread` (e.g., target 60 FPS).
    *   Further review and optimize `ZoneOwnershipCalculator` sampling if performance issues arise on target devices.
    *   Monitor `PaintSurface` bitmap memory usage, especially during active gameplay and on lower-end devices.
2.  **Audio / FX Integration (M-13):**
    *   Integrate `SoundPool` for sound effects (painting, mode toggle, UI clicks, match start/end).
    *   Add simple particle effects for paint splats (optional, if performance allows).
3.  **Polish & Release Preparations (M-14):**
    *   Create app icons and promotional graphics.
    *   Develop a simple onboarding experience for new users (e.g., brief tutorial pop-ups).
    *   Thorough QA testing on various devices and Android versions.
    *   Write and include a Privacy Policy.
    *   Prepare and test Android App Bundle for Google Play Store submission.
    *   Address any outstanding bugs or minor UI/UX issues.
4.  **(Deferred) `RoomSequenceLevel` / `LevelManager`:**
    *   If pursued, implement a new `Level` type for sequenced rooms.
    *   Create a `LevelManager` to control the sequence of levels loaded during a match.

### 10.4 Packaging, Distribution, and Deployment
*   **Packaging:** Android App Bundle (`.aab`) will be generated for distribution.
*   **Distribution:** Primarily through the Google Play Store.
*   **Deployment:**
    *   Firebase backend (RTDB, Auth, App Check) is already deployed and managed by Google.
    *   Updates to the Android app will be rolled out via the Google Play Store.
    *   Database rules for Firebase will be deployed via the Firebase console or CLI.

---

## 11. Potential Codebase Improvements

This section outlines potential areas for future refactoring, optimization, or enhancement beyond the immediate roadmap.

*   **Enhanced Error Handling & User Feedback:**
    *   Implement more user-friendly error messages for Firebase connectivity issues or matchmaking failures (e.g., "Could not connect to server, please check your internet connection").
    *   Consider retry mechanisms for failed Firebase operations where appropriate.
*   **Code Modularity:**
    *   `GameView.kt` is substantial. Consider breaking down its responsibilities further, e.g., separating rendering logic for different HUD elements or player drawing into helper classes or methods.
    *   `MultiplayerManager.kt` is also very large. It could potentially be refactored into smaller, more focused services (e.g., `MatchmakingService`, `GameStateSyncService`, `RematchService`).
*   **Centralized Constants:**
    *   While many constants are in companion objects, conduct a pass to ensure all hardcoded strings (especially Firebase node names, event names, or UI text that isn't for direct display) are defined as constants in appropriate locations.
*   **Advanced Game Loop:**
    *   The current `GameThread` uses `Thread.sleep()` for basic frame pacing. A more advanced game loop could use `System.nanoTime()` for precise frame timing and potentially variable timesteps or fixed timesteps with interpolation for smoother rendering under varying loads.
*   **Testing Strategy Expansion:**
    *   Increase unit test coverage for game logic classes (`Player`, `MazeLevel`, `GameModeManager`).
    *   Develop integration tests for `MultiplayerManager` interactions (this would require mocking Firebase, which can be complex but valuable).
    *   Continue to expand and maintain the Espresso UI test suite (`GameFlowIntegrationTest.kt` and others) for common user flows in `HomeActivity` and `MainActivity`. Focus on creating tests that are robust against minor UI changes and timing variations to minimize flakiness.
*   **Configuration Management:**
    *   Values like `sampleStep = 10` in `GameView.update` for `ZoneOwnershipCalculator`, or joystick sensitivity parameters, could be made configurable (e.g., through constants, or even remote config for A/B testing if desired in the future).
*   **State Management in Activities/Fragments:**
    *   Review state saving and restoration during Activity/Fragment lifecycle events, especially for `MainActivity` during a match if the app is backgrounded. `PaintSurface` persistence is a good step.
*   **Input Validation:**
    *   Strengthen client-side input validation (e.g., for player name, catchphrase in `ProfileFragment`).
    *   Rely on Firebase Rules for server-side validation of data written to the database.
*   **Dependency Injection:**
    *   For larger-scale maintainability and testability, consider introducing a simple dependency injection framework or manual DI for managing dependencies like `MultiplayerManager`, `ProfileRepository`.
*   **Code Comments & Documentation:**
    *   Ensure complex algorithms or non-obvious logic sections are well-commented.
    *   Keep KDoc updated for public APIs of classes and methods.
*   **UI Adjustments in `activity_main.xml`:**
    *   `TimerHudView`: Adjusted `layout_width`, `layout_height`, and `layout_marginTop`.
    *   `ZoneHudView`: Positioned below `TimerHudView` in the top-right corner. Adjusted `layout_width` and `layout_height`.

**2025-06-02**
- **Stabilized `GameFlowIntegrationTest.kt`:**
    - Addressed flakiness and `RootViewWithoutFocusException` errors in UI tests.
    - Simplified Firebase setup and cleanup in test environment to prevent interference with activity lifecycle and UI thread.
    - Made the `joinRandomGameAndSeeSearchingMessage` test more robust by handling various outcomes gracefully and being less dependent on exact UI states or timings.
    - Removed `simpleAdditionTest` as it was a redundant placeholder.
    - Ensured tests reliably pass when run individually and as a suite, improving confidence in core game flow stability.
*   **UI Adjustments in `activity_main.xml`:**
    *   `TimerHudView`: Adjusted `layout_width`, `layout_height`, and `layout_marginTop`.
    *   `ZoneHudView`: Positioned below `TimerHudView` in the top-right corner. Adjusted `layout_width` and `layout_height`.

**2025-06-02**
- **Stabilized `GameFlowIntegrationTest.kt`:**
    - Addressed flakiness and `RootViewWithoutFocusException` errors in UI tests.
    - Simplified Firebase setup and cleanup in test environment to prevent interference with activity lifecycle and UI thread.
    - Made the `joinRandomGameAndSeeSearchingMessage` test more robust by handling various outcomes gracefully and being less dependent on exact UI states or timings.
    - Removed `simpleAdditionTest` as it was a redundant placeholder.
    - Ensured tests reliably pass when run individually and as a suite, improving confidence in core game flow stability.
- **Implemented Unit Tests for `Player.kt` (`PlayerTest.kt`):**
    - Created comprehensive unit tests for the `Player` class using JUnit and Mockito.
    - Covered core functionalities including mode switching, ink depletion/refill logic under various conditions (correct color, different color, boundary limits), ink percentage calculation, basic player movement mechanics (position updates, boundary coercion), and collision-based movement (no collision, full collision, sliding along X/Y axes).
    - Resolved issues related to mocking Android SDK dependencies (e.g., `android.graphics.Paint`) by configuring `testOptions { unitTests.returnDefaultValues = true }` in `build.gradle`.
    - Addressed and fixed a subtle bug in test logic where mock setups for `PaintSurface.getPixelColor` did not account for player position changes before the color check, ensuring accurate testing of ink refill conditions.

---

## 12. Risks & Mitigations

| Risk                             | Impact   | Mitigation                                                                                                        |
| :------------------------------- | :------- | :---------------------------------------------------------------------------------------------------------------- |
| Real-time sync drift             | Low      | Addressed with normalized coordinates & server-timestamped events. Latency inherent in mobile networks.            |
| Coverage/Zone sampling performance | Moderate | `sampleStep` is tunable. If issues arise, further optimization (e.g., less frequent updates, worker thread) may be needed. |
| Memory growth from `PaintSurface`| Moderate | `PaintSurface` bitmap is reused. Monitor on various devices. For very large mazes or long sessions, this could be a concern. |
| Firebase cost                    | Medium   | Structure data for shallow queries. Minimize write frequency. Stale game cleanup implemented. Monitor usage.      |
| Security of Firebase Rules       | Medium   | Regularly review and test Firebase Database Rules to ensure data integrity and prevent unauthorized access.         |
| Player Churn / Engagement        | High     | Introduce new features (modes, levels, customization) progressively. Gather user feedback.                      |
| Scalability of `findRandomAvailableGame` | Moderate | Current implementation scans limited recent games. For very high volume, a more sophisticated matchmaking queue might be needed. |

---

## 13. Appendix – File Map

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
    │   ├─ ZoneOwnershipCalculator.kt
    │   └─ ZoneHudView.kt
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

### 13.1 References
*   *(Placeholder for links to relevant documentation, e.g., Firebase docs, Kotlin style guides, Android developer guides)*

### 13.2 Glossary
*(Combined with Section 0.2 Definitions, Acronyms, and Abbreviations)*


### 13.3 Change Log
**2025-05-19**
- Integrated player profile data (name, favorite color) into match setup and display.
- Added fallback logic for duplicate or missing favorite colors.
- Ensured `GameView.initGame` and `setLocalPlayerId` are always called at match start, not just rematch.
- Fixed bug where game loop would not start due to missing thread initialization.
- Fixed bug where save button could be enabled before profile was loaded.
- Improved friend code generation and uniqueness logic.
- Added logging for profile loading, friend code generation, and game initialization.

**2025-05-20**
- Implemented host-authoritative timer sync: host writes a synchronized `startTime` to Firebase, all clients read this value before starting the match timer. Ensures all players' timers are aligned to the same reference.

**2025-05-21**
- Painted surfaces are now persisted across app backgrounding and SurfaceView recreation. All Player objects are updated to reference the new PaintSurface after recreation, ensuring painting works after resume.
- CoverageCalculator now only samples within the maze bounds, resulting in accurate coverage percentages.
- GameView now more robustly determines the height of the coverage HUD to avoid drawing the maze beneath it.
- Player class surface property is now mutable (var) to allow updating after surface recreation.

**2025-05-22**
- **Implemented Zones Game Mode:**
    - Added `getZones()` to `Level` interface and `MazeLevel` (defines 6 zones).
    - Created `ZoneOwnershipCalculator` to determine zone control by sampling `PaintSurface`.
    - Created `ZoneHudView` to display zone ownership as a mini-map grid.
    - Updated `HomeActivity` to include "Zones" in game mode selection for hosts.
    - Updated `MainActivity` to pass game mode to `GameView` and manage HUD visibility.
    - Updated `GameView` to handle Zones mode logic:
        - Calls `ZoneOwnershipCalculator` and updates `ZoneHudView`.
        - Hides `CoverageHudView` in Zones mode and vice-versa.
        - Determines win condition based on zone control in `finishMatch`.
        - UI updates for HUDs moved to main thread handler.
    - Updated `MultiplayerManager`:
        - `GameSettings` data class now includes `gameMode`.
        - `hostGame` and `joinGame` now sync `gameMode` via Firebase.
    - Added `getBitmap()` to `PaintSurface` for direct (non-copy) bitmap access by `ZoneOwnershipCalculator`.
    - Ensured `PaintSurface` is cleared in `GameView.initGame()` to prevent paint from persisting between matches.
    - Optimized `sampleStep` for `ZoneOwnershipCalculator` in `GameView.update()` to improve performance.
- **UI Adjustments in `activity_main.xml`:**
    - `TimerHudView`: Adjusted `layout_width`, `layout_height`, and `layout_marginTop`.
    - `ZoneHudView`: Positioned below `TimerHudView` in the top-right corner. Adjusted `layout_width` and `layout_height`.

**2025-06-02**
- **Stabilized `GameFlowIntegrationTest.kt`:**
    - Addressed flakiness and `RootViewWithoutFocusException` errors in UI tests.
    - Simplified Firebase setup and cleanup in test environment to prevent interference with activity lifecycle and UI thread.
    - Made the `joinRandomGameAndSeeSearchingMessage` test more robust by handling various outcomes gracefully and being less dependent on exact UI states or timings.
    - Removed `simpleAdditionTest` as it was a redundant placeholder.
    - Ensured tests reliably pass when run individually and as a suite, improving confidence in core game flow stability.
- **Implemented Unit Tests for `Player.kt` (`PlayerTest.kt`):**
    - Created comprehensive unit tests for the `Player` class using JUnit and Mockito.
    - Covered core functionalities including mode switching, ink depletion/refill logic under various conditions (correct color, different color, boundary limits), ink percentage calculation, basic player movement mechanics (position updates, boundary coercion), and collision-based movement (no collision, full collision, sliding along X/Y axes).
    - Resolved issues related to mocking Android SDK dependencies (e.g., `android.graphics.Paint`) by configuring `testOptions { unitTests.returnDefaultValues = true }` in `build.gradle`.
    - Addressed and fixed a subtle bug in test logic where mock setups for `PaintSurface.getPixelColor` did not account for player position changes before the color check, ensuring accurate testing of ink refill conditions.

**2025-06-02**
- **Stabilized `GameFlowIntegrationTest.kt`:**
    - Addressed flakiness and `RootViewWithoutFocusException` errors in UI tests.
    - Simplified Firebase setup and cleanup in test environment to prevent interference with activity lifecycle and UI thread.
    - Made the `joinRandomGameAndSeeSearchingMessage` test more robust by handling various outcomes gracefully and being less dependent on exact UI states or timings.
    - Removed `simpleAdditionTest` as it was a redundant placeholder.
    - Ensured tests reliably pass when run individually and as a suite, improving confidence in core game flow stability.

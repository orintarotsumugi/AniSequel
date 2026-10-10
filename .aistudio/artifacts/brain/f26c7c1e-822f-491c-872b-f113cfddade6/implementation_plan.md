# Anime Dynamic Theme, Timed Revert, and Theme Settings Polish

Enable immersive anime color adaptation with timer-based reverts, customizable app background color, retainable anime color for Material You design, and a retouched, polished theme settings layout.

## User Review & Critical Decisions

> [!IMPORTANT]
> - **Confirmed Choices**: Smooth spring physics animation matching Material You during anime theme transitions, and flexible revert duration options (5 minutes, 10 minutes, or immediately upon leaving).
> - **Anime Theme & Background**: Toggling an anime theme applies its dominant cover/banner color, switches to an immersive dark mode (true black style), and adapts the UI dynamically.
> - **Settings Layout**: Retouching the Theme settings tab for pristine organization, responsive layout, and smooth spring feedback.

## 1. Overview & Core Concept

- **What It Does**: Allows users to toggle an anime's dominant color as the active app theme, adapting the UI with Material You style spring animations and dark immersion. Includes configurable timer options (immediately upon leaving anime info, 5 min, or 10 min) to revert back to the original theme, plus full app background color customization and settings layout refinement.
- **Key Value**: Deep personal immersion into favorite anime aesthetics without losing user's default configuration.

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. *Theme Settings*: Configure Anime Theme options (enable/disable, retain anime color for Material You, revert timer duration: Immediately / 5m / 10m) and custom full app background color in a beautifully retouched settings layout.
  2. *Anime Theme Activation*: Tap/toggle anime theme on an anime card or detail sheet.
  3. *Immersive Adaptation*: UI transitions smoothly with spring physics into the anime's color scheme and dark background.
  4. *Timed Revert*: Automatically reverts after the selected duration or immediately upon exiting the anime info tab.
- **Visual Identity**: Material Design 3 Expressive, spring physics animations (`ExpressiveMotion`), true black immersive surfaces, and clean token-based styling.

## 3. Key Product Decisions & Trade-Offs

- **Decision 1**: Timer-based Revert & Lifecycle Management
  - *Chosen Approach*: Coroutine-backed delay timer paired with navigation/dismissal observation in `SequelDetailSheet` and ViewModel state.
  - *Why*: Ensures reliable automatic reversion according to user preference without battery drain.
- **Decision 2**: Theme Preferences DataStore Extension
  - *Chosen Approach*: Store anime theme state, retain color flag, revert duration, and custom background color in `ThemePreferences`.
  - *Why*: Persistent across app restarts and seamlessly integrated with `AniSequelTheme`.

## 4. Technical Architecture & Data Strategy

```
┌─────────────────────────┐       ┌─────────────────────────┐
│  SequelDetailSheet /    │       │     ThemePreferences    │
│  Anime Card Action      │──────>│  (DataStore Persistence)│
└─────────────────────────┘       └─────────────────────────>
             │                                   │
             ▼                                   ▼
┌─────────────────────────┐       ┌─────────────────────────┐
│     Timer & Revert      │       │      AniSequelTheme     │
│  (Immediately / 5m / 10m│──────>│  (Dynamic Color &       │
└─────────────────────────┘       │   Anime Color Scheme)   │
                                  └─────────────────────────┘
```

- **Data Model**: `ThemeSettings` augmented with `animeThemeActive`, `animeThemeColorHex`, `animeThemeRetainColor`, `animeThemeRevertDuration`, and `appBackgroundColor`.
- **State & Animation**: Uses `animateColorAsState` with expressive spring specs for seamless color morphing.

## Unreleased
- Added a Style option to the Player Head HUD: Face, Flat (the default, same as before) or 3D
- Needs PolyCompose 3.0.7

## 3.10.1
- Performance improvements: the media and lyrics HUDs do much less work while idle, and several HUDs allocate less each frame
- Fixed sprites not loading from protected server resource packs

## 3.10.0
- Updated to OneConfig 1.9.0, which needs PolyCompose installed
- Split Armor Status into one HUD per slot, grouped by default so they move and resize together (#282)
- Armor Status can be added from the HUD library as one card
- The Horizontal, Vertical and Reverse Order arrangement of the armor HUDs lives in the HUD group settings
- Empty armor slots take no space outside the editor, so anchored HUDs close up
- Vanilla style armor slots that touch join into one hotbar strip, with extra info drawn outside the slot
- Existing Armor Status settings and layout are migrated automatically

## 3.9.19
- Updated to OneConfig 1.8.0
- Added FPS lows, and percentiles are now shown as FPS
- Added an option to show only the number in the keystrokes CPS counter
- Added more HUD options and fixed several HUD bugs
- Anchored the lyrics HUD to the media card
- Performance improvements

## 3.9.18
- Use vanilla FPS counter for FPS hud
- Fixed media hud controlling music even when It's not actually shown
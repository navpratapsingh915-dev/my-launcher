# Minimal Launcher (Niagara-style)

Kotlin + Jetpack Compose. Open this folder in Android Studio (Koala or newer), let Gradle sync, press Run.
Then press the Home button on the phone and choose "Launcher" -> "Always".

Features: wallpaper-through UI, clock + date, favourites list (themed monochrome icons on Android 13+),
A-Z rail with bending letters + bubble + haptics, sectioned app list, search (Enter launches first match),
long-press menu (add/remove favourite, app info, uninstall), auto refresh on install/uninstall.

Code: Data.kt (apps, favourites, icon cache), Ui.kt (all screens), MainActivity.kt.

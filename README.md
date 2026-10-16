# Kreuz2 – Hello World

Eine kleine native Android-App mit „Hello World!“.

## APK ohne Android Studio bauen

1. Öffne im GitHub-Repository **Actions → Android APK**.
2. Der Build startet bei jedem Push auf `main`. Du kannst ihn auch mit **Run workflow** starten.
3. Öffne den erfolgreichen Lauf und lade unter **Artifacts** `kreuz2-debug-apk` herunter.
4. Entpacke die ZIP-Datei und installiere `app-debug.apk` auf deinem Android-Gerät. Erlaube bei Bedarf die Installation aus dieser Quelle.

Die APK ist zum Testen mit einem Debug-Schlüssel signiert. Android 6.0 oder neuer wird benötigt.

## Entwicklung

Der Bildschirm liegt in `app/src/main/java/de/steffen/kreuz2/MainActivity.java`, Texte in `app/src/main/res/values/strings.xml`.
Du kannst die Dateien direkt auf GitHub bearbeiten und committen; Actions baut anschließend eine neue APK.

Der Build verwendet Java 17, Gradle 8.11.1, Android Gradle Plugin 8.9.2 und Android SDK 35. Gradle wird im Workflow eingerichtet; ein Gradle-Wrapper ist noch nicht enthalten.

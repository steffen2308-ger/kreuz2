# Kreuz2 – Kreuzworträtsel aus Screenshots

Native Android-App zum Öffnen und Ausfüllen eines Kreuzworträtsel-Screenshots. Die Verarbeitung findet offline auf dem Gerät statt, ohne Internet- oder pauschale Speicherberechtigung.

## APK ohne Android Studio

Unter **Actions → Android APK** den neuesten erfolgreichen Lauf öffnen. Bei **Artifacts** `kreuz2-debug-apk` herunterladen, ZIP entpacken und `app-debug.apk` installieren. Android 6.0 oder neuer wird benötigt. Das ist eine mit einem Debug-Schlüssel signierte Testversion. Nach Änderungen auf `main` baut GitHub automatisch; alternativ **Run workflow**.

Ab dieser Version verwendet die Test-App einen festen, bewusst öffentlichen Entwicklungsschlüssel (`app/debug.keystore`, Passwort `android`). Dadurch lassen sich spätere Testversionen über diese Version installieren, ohne die Spielstände zu verlieren. Der Schlüssel ist nicht für eine Veröffentlichung bestimmt. Eine zuvor installierte Hello-World-APK wurde noch mit einem anderen Schlüssel gebaut und muss einmal deinstalliert werden, falls Android das Update deshalb ablehnt.

## Bedienung

1. **Bild öffnen** ruft die Android-Dateiauswahl auf. Der Screenshot wird intern kopiert; die Originaldatei bleibt unverändert.
2. Die App sucht regelmäßige horizontale und vertikale dunkle Linien und zeigt **Spalten × Zeilen**. **Linien** schaltet das erkannte Raster über dem Bild ein/aus. Orange sind vermutete Hinweisfelder, rot gesperrte Felder.
3. Über **Mehr → Raster korrigieren** die vier Ecken auf die äußeren Rastergrenzen ziehen und Spalten/Zeilen eingeben. Die Vorschau passt sich an. Bei unsicherer Erkennung startet die Korrektur mit einem ausdrücklich als Platzhalter bezeichneten 10 × 10 Raster.
4. Ein freies Feld antippen und über die Gerätetastatur schreiben. Einfügen mehrerer Buchstaben ist ebenfalls möglich. Das Originalbild bleibt sichtbar. Erneutes Antippen des ausgewählten Feldes oder die Richtungstaste schaltet waagerecht/senkrecht um. Lange auf die Richtungstaste drücken erlaubt zusätzlich links/oben. Hardware-Pfeiltasten bewegen die Auswahl.
5. **Auto** berücksichtigt Pfeile benachbarter Hinweisfelder und freie Nachbarfelder. Am rechten Wortende geht es nach unten, wenn dort ein freies Feld liegt; entsprechend von senkrecht nach rechts. Ist beides versperrt, wird das nächste leere Schreibfeld gesucht. Bei ausgeschaltetem Auto bleibt die Richtung fest und die Auswahl stoppt an Kanten/Hindernissen.
6. Lange auf ein Feld drücken: **Schreibfeld**, **gesperrt**, **Hinweisfeld** oder **Pfeilrichtung** korrigieren. **↶ Zurück** nimmt bis zu 50 Bearbeitungsschritte der Sitzung zurück, auch Rasteränderungen. Dieser Undo-Verlauf wird nicht mit dem Spielstand gespeichert.
7. Mit zwei Fingern zoomen und mit einem Finger verschieben; **Mehr → Ansicht einpassen** setzt die Ansicht zurück.

## Speichern und Fortsetzen

- Eingaben werden nach kurzer Pause und beim Verlassen der App automatisch lokal gespeichert. **Speichern** vergibt einen Namen. **Spielstände** öffnet begonnene Rätsel; beim Neustart wird das zuletzt geöffnete geladen.
- **Mehr → Spielstand exportieren** schreibt eine `.kreuz2.json` Datei in einen selbst gewählten Ordner. Darin stecken Bild, Raster, Buchstaben, Feldtypen, Pfeile und Eingaberichtung. Mit **Spielstand importieren** lässt sich eine solche Datei als neues lokales Rätsel öffnen.
- Eine Deinstallation löscht die lokalen Spielstände. Exportierte Dateien bleiben erhalten. Der ursprüngliche Screenshot ist kein Spielstand.

## Grenzen der Erkennung

Die Erkennung ist eine Bildheuristik für gerade, rechteckige Screenshots mit sichtbaren dunklen Linien auf hellem Grund. Sie analysiert Linien, Tintenanteil, Textzeilen und kleine ausgefüllte Pfeilformen; sie liest die Hinweise nicht inhaltlich und löst keine Wörter. Standardmäßige kleine Pfeile nach rechts/unten/links/oben werden gesucht. Andere Pfeilstile, schräge Fotos, fehlende Außenlinien, sehr kleine oder unregelmäßige Raster und bereits beschriebene Rätsel können Korrekturen erfordern. Hinweisfelder und Pfeile bitte prüfen. Bilddateien sind auf 35 MB begrenzt und werden für die Anzeige auf höchstens ca. 2400 Pixel Kantenlänge reduziert; die Analyse arbeitet mit höchstens 1200 Pixeln. Raster bis 100 × 100 werden unterstützt.

## Projekt und Prüfung

- Java 17, Gradle 8.11.1, Android Gradle Plugin 8.9.2, SDK/Target 35, Minimum SDK 23. Gradle wird im Workflow eingerichtet; ein Wrapper ist nicht enthalten.
- `GridDetector.java`: unabhängige Offline-Bildanalyse.
- `Puzzle.java`: Raster, Felder, Buchstaben und Navigation.
- `PuzzleView.java`: Bild, Linien, Buchstaben, Auswahl, Zoom und Rasterkorrektur.
- `PuzzleStore.java`: atomare lokale Speicherung und portabler Export/Import.
- `MainActivity.java`: Dateiauswahl, Bearbeitungsablauf, Tastatur und Spielstände.
- `LetterInput.java`: einmalige Übernahme bestätigter Tastatureingaben, inklusive zusammengesetzter Zeichen und Einfügen.
- `app/src/test/`: Tests mit synthetischen Screenshots für Raster, graue Linien, fehlende Innenlinie, Hinweis-/Sperrfelder, Pfeilformen sowie Navigation an Rändern und Hindernissen.

GitHub Actions führt `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` aus. Testergebnisse und Lint-Bericht werden zusätzlich als `test-and-lint-reports` hochgeladen. Die Tests ersetzen keinen Gerätetest mit echten Screenshots und unterschiedlichen Android-Tastaturen.

Zusätzlich prüfen Android-Tests mit Robolectric den Datei-Rundlauf inklusive Bild, erneutes lokales Speichern, Tastatur-Komposition und den Start der App.

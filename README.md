# Kreuz2 – Kreuzworträtsel aus Screenshots

Native Android-App zum Öffnen und Ausfüllen eines Kreuzworträtsel-Screenshots. Die Bildverarbeitung findet lokal auf dem Gerät statt, ohne pauschale Speicherberechtigung. Eine bewusst gestartete Google-Suche öffnet den Browser und benötigt Internet.

## APK ohne Android Studio

Unter **Actions → Android APK** den neuesten erfolgreichen Lauf öffnen. Bei **Artifacts** `kreuz2-debug-apk` herunterladen, ZIP entpacken und `app-debug.apk` installieren. Android 6.0 oder neuer wird benötigt. Das ist eine mit einem Debug-Schlüssel signierte Testversion. Nach Änderungen auf `main` baut GitHub automatisch; alternativ **Run workflow**.

Ab dieser Version verwendet die Test-App einen festen, bewusst öffentlichen Entwicklungsschlüssel (`app/debug.keystore`, Passwort `android`). Dadurch lassen sich spätere Testversionen über diese Version installieren, ohne die Spielstände zu verlieren. Der Schlüssel ist nicht für eine Veröffentlichung bestimmt. Eine zuvor installierte Hello-World-APK wurde noch mit einem anderen Schlüssel gebaut und muss einmal deinstalliert werden, falls Android das Update deshalb ablehnt.

## Kompakte Oberfläche

Keine Überschrift. Eine 34-dp-Werkzeugleiste enthält Bild öffnen, Auto, Linien, Rückgängig und Mehr. Der große Richtungspfeil sitzt direkt neben der Tastatureingabe in einer gemeinsamen 48-dp-Zeile. Spielstände und Speichern sind unter Mehr erreichbar. Eine einzelne schmale Statuszeile zeigt Zeilen/Spalten, Auswahl und Speicherstatus. Dadurch bleibt deutlich mehr Bildschirmhöhe für das Rätsel.

## Bedienung

1. **Bild öffnen** ruft die Android-Dateiauswahl auf. Der Screenshot wird intern kopiert; die Originaldatei bleibt unverändert.
2. Die App sucht regelmäßige horizontale und vertikale dunkle Linien und zeigt **Spalten × Zeilen**. **Linien** schaltet das erkannte Raster über dem Bild ein/aus. Orange sind vermutete Hinweisfelder, rot gesperrte Felder.
3. Über **Mehr → Raster korrigieren** die vier Ecken auf die äußeren Rastergrenzen ziehen und Spalten/Zeilen eingeben. Die Vorschau passt sich an. Bei unsicherer Erkennung startet die Korrektur mit einem ausdrücklich als Platzhalter bezeichneten 10 × 10 Raster.
4. Ein freies Feld antippen und über die Gerätetastatur schreiben: Jeder Buchstabe erscheint sofort und die Auswahl rückt weiter, ohne Enter. Das gilt auch, wenn die Tastatur noch eine vorläufige Eingabe meldet. Einfügen mehrerer Buchstaben ist ebenfalls möglich. Das Originalbild bleibt sichtbar. Erneutes Antippen des ausgewählten Feldes (auch schnelles Doppeltippen) oder der große Pfeil schaltet waagerecht/senkrecht um. Ein großer weißer Pfeil auf türkisem Grund direkt über der Tastatur zeigt die aktuelle Schreibrichtung; er lässt sich ebenfalls zum Umschalten antippen. Lange auf den Pfeil drücken erlaubt zusätzlich links/oben. Hardware-Pfeiltasten bewegen die Auswahl.
5. **Auto** berücksichtigt Pfeile benachbarter Hinweisfelder und freie Nachbarfelder. Am rechten Wortende geht es nach unten, wenn dort ein freies Feld liegt; entsprechend von senkrecht nach rechts. Ist beides versperrt, wird das nächste leere Schreibfeld gesucht. Bei ausgeschaltetem Auto bleibt die Richtung fest und die Auswahl stoppt an Kanten/Hindernissen.
6. Ein Feld antippen und **Mehr → Feldtyp/Pfeil bearbeiten** öffnen: **Schreibfeld**, **gesperrt**, **Hinweisfeld** oder **Pfeilrichtung** korrigieren. **↶ Zurück** nimmt bis zu 50 Bearbeitungsschritte der Sitzung zurück, auch Rasteränderungen. Dieser Undo-Verlauf wird nicht mit dem Spielstand gespeichert.
7. Mit zwei Fingern zoomen und mit einem Finger verschieben; **Mehr → Ansicht einpassen** setzt die Ansicht zurück.

## Fragetext in Google suchen

Einen Fragetext ungefähr **zwei Sekunden gedrückt halten**. Sobald das Handy kurz vibriert, wird der Text dieses Feldes lokal erkannt und Google im Browser mit `Fragetext Kreuzworträtsel Lösung` geöffnet. Du musst dazu nicht erst loslassen. Kurzes Tippen bleibt für die Eingabe; Verschieben, Zoomen und Loslassen vor zwei Sekunden brechen das Halten ab.

Das OCR-Modell steckt in der APK und braucht keinen separaten Modelldownload. Der Screenshot wird nicht als Suchbild hochgeladen. Nur der erkannte Suchtext wird an Google übergeben. Bei zu kleinem/unleserlichem Text oder einem Erkennungsfehler erscheint ein Eingabefeld für die Suchfrage; dort ist auch die Feldkorrektur erreichbar. Schwarze Felder öffnen beim Halten weiterhin ihre Feldkorrektur. Bei einem in mehrere Fragen geteilten Hinweisfeld kannst du den gewünschten Suchtext über den Browser anpassen. Google-Ergebnisse werden nicht automatisch in das Rätsel eingetragen.

## Speichern und Fortsetzen

- Eingaben werden nach kurzer Pause und beim Verlassen der App automatisch lokal gespeichert. **Mehr → Spielstand speichern** vergibt einen Namen. **Mehr → Spielstände öffnen** öffnet begonnene Rätsel; beim Neustart wird das zuletzt geöffnete geladen.
- **Mehr → Spielstand exportieren** schreibt eine `.kreuz2.json` Datei in einen selbst gewählten Ordner. Darin stecken Bild, Raster, Buchstaben, Feldtypen, Pfeile und Eingaberichtung. Mit **Spielstand importieren** lässt sich eine solche Datei als neues lokales Rätsel öffnen.
- Eine Deinstallation löscht die lokalen Spielstände. Exportierte Dateien bleiben erhalten. Der ursprüngliche Screenshot ist kein Spielstand.

## Grenzen der Erkennung

Die Rastererkennung passt den Schwellwert an den lokalen Bildhintergrund an und wertet mehrere mögliche Linienabstände aus. Waagerechte und senkrechte Linien müssen sich im selben Bildbereich gegenseitig stützen. Bis zu zwei benachbarte fehlende Innenlinien werden ergänzt; fehlende Außenlinien werden nur aus fortgesetzten Linien der Gegenachse abgeleitet. Kurze Trennlinien in Hinweisfeldern und Bildschirmelemente außerhalb des Rasters werden gesondert bewertet.

Zwölf zusätzliche synthetische Screenshots prüfen blasse und farbige Linien, Unterbrechungen, mehrere fehlende Linien, fehlende Außenkanten, Skalierung, kleine Raster, zusätzliche Hinweistrenner, schwarze Flächen, andere kleine Raster im Bild und ungleichmäßigen Hintergrund. Weitere Prüfungen verhindern erfundene Raster auf Text, einfarbigen Flächen, Streifen und Bildrauschen. Dies ist keine Garantie für jeden realen Screenshot.

Die Erkennung ist eine Bildheuristik für gerade, rechteckige Screenshots mit sichtbaren dunklen Linien auf hellem Grund. Sie analysiert Linien, Tintenanteil, Textzeilen und kleine ausgefüllte Pfeilformen; sie versteht die Hinweise nicht inhaltlich und löst keine Wörter. Für die auf Wunsch gestartete Google-Suche liest eine zusätzliche lokale Texterkennung (ML Kit, lateinische Schrift) den ausgewählten Feldausschnitt. Standardmäßige kleine Pfeile nach rechts/unten/links/oben werden gesucht. Andere Pfeilstile, schräge Fotos, stark beschnittene Außenbereiche, sehr kleine oder unregelmäßige Raster und bereits beschriebene Rätsel können Korrekturen erfordern. Hinweisfelder und Pfeile bitte prüfen. Bilddateien sind auf 35 MB begrenzt und werden für die Anzeige auf höchstens ca. 2400 Pixel Kantenlänge reduziert; die Analyse arbeitet mit höchstens 1200 Pixeln. Raster bis 100 × 100 werden unterstützt.

## Projekt und Prüfung

- Java 17, Gradle 8.11.1, Android Gradle Plugin 8.9.2, SDK/Target 35, Minimum SDK 23. Gradle wird im Workflow eingerichtet; ein Wrapper ist nicht enthalten.
- `GridDetector.java`: adaptive Offline-Bildanalyse und Feldklassifikation.
- `GridLocator.java`: gemeinsame Suche beider Rasterachsen und Rekonstruktion belegter Lücken.
- `Puzzle.java`: Raster, Felder, Buchstaben und Navigation.
- `PuzzleView.java`: Bild, Linien, Buchstaben, Auswahl, Zoom und Rasterkorrektur.
- `PuzzleStore.java`: atomare lokale Speicherung und portabler Export/Import.
- `MainActivity.java`: Dateiauswahl, Bearbeitungsablauf, Tastatur und Spielstände.
- `LetterInput.java`: einmalige Übernahme bestätigter Tastatureingaben, inklusive zusammengesetzter Zeichen und Einfügen.
- `app/src/test/`: Tests mit synthetischen Screenshots für Raster, graue Linien, fehlende Innenlinie, Hinweis-/Sperrfelder, Pfeilformen sowie Navigation an Rändern und Hindernissen.

GitHub Actions führt `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` aus. Testergebnisse und Lint-Bericht werden zusätzlich als `test-and-lint-reports` hochgeladen. Die Tests ersetzen keinen Gerätetest mit echten Screenshots und unterschiedlichen Android-Tastaturen.

Zusätzlich prüfen Android-Tests mit Robolectric den Datei-Rundlauf inklusive Bild, erneutes lokales Speichern, Tastatur-Komposition und den Start der App.

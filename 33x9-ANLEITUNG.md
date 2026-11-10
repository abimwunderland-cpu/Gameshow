# Wolfsfell Gameshow 33x9 – Installation

1. Backup von Plugins/WolfsfellGameshow und der Welt machen.
2. GitHub-Source-ZIP entpacken und Dateien ins Repository übernehmen. GitHub Actions: Maven-Build prüfen.
3. JAR ersetzen, Server neu starten, das bereits funktionierende Buzzer-Ressourcenpack beibehalten.
4. Die **neue** 33x9-Wand mit 297 festen Hintergrundblöcken und freier Vorderseite vorbereiten. Alte 9x9-Frames beim Umbau entfernen; der Befehl `/wg screen set` entfernt nur Frames, die der laufenden Plugin-Instanz bekannt sind.
5. Den unteren linken Wandblock ansehen und `/wg screen set` ausführen. ACHTUNG: Es werden 297 Karten/Itemframes erstellt.
6. `/wg screen test` testen. Die beiden Leaderboards erscheinen dauerhaft; Game 1 zeigt mittig die Items.
7. Kandidaten wie bisher auswählen, `/wg start` verwenden. Stufen 1–7: 7/6/5/4/3/2/1 Punkte; der Buzzer friert die Stufe ein.
8. Nach Serverneustart werden Karten nur automatisch wieder angebunden, wenn ihre Chunks geladen sind. Bei leerer Wand Chunks laden und `/wg screen set` erneut durchführen (vorher alte Frames manuell entfernen).

**Wertung:** Team-Anzeige = Qualifikationspunkte + laufende Quizpunkte. Persönliche Anzeige = gespeicherte individuelle Punkte. Gleiche Rohwerte teilen sich bei `rankRoundSafe()` bereits den Mittelwert der belegten Platzierungs-Punkte, also nicht zwangsläufig exakt denselben Rangwert wie ein Einzelplatz. Die 7 Stufen werden als Quiz-Rohpunkte gezählt.

**Grenzen:** Das Plugin verwendet weiterhin die vorhandenen 30 Itembilder; echte neue Vanilla-Texturen sind in diesem Update noch nicht enthalten. Eine vollautomatische Wiederherstellung bei ungeladenen Chunks ist noch offen. Ungetesteter Quellcode, nicht als erfolgreicher Paper-Build ausgegeben.

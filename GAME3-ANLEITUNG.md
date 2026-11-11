# Wolfsfell Gameshow – Game 3 Eisbootsrennen (Quellcode)

**Achtung:** Quellcode-Paket; ein erfolgreicher Java-/Paper-Build wurde in dieser Umgebung nicht verifiziert. Bitte zuerst auf einem Testserver testen.

## Einrichtung
1. GitHub **Gameshow-Repository** benutzen, nicht Junkiecraft. Dateien des ZIPs ins Gameshow-Repo hochladen und GitHub Actions Build starten.
2. Die neue JAR installieren, Server neu starten.
3. `/wg wand game3` – du erhältst einen speziellen Game-3-Markierungsstab.
4. Shift + Rechtsklick wechselt den Modus. Rechtsklick speichert die eigene Position/Blickrichtung; Linksklick löscht die letzte Markierung im Modus.
5. Markiere in der Reihenfolge acht Bootspositionen, Fahrer-Wartepunkt, Zuschauer-Spawn, beide Ecken des 3D-Zuschauerbereichs und die beiden Endpunkte der Ziellinie.
6. Die Ziellinie muss QUER zur Fahrtrichtung liegen. Die Richtung ist durch die Reihenfolge A->B definiert; falls Runden nicht zählen, die Zielpunkte in umgekehrter Reihenfolge markieren.
7. Kandidaten wie bisher auswählen: `/wg candidate set <Team> <Spieler>`.
8. `/wg tp game 3` teleportiert Kandidaten zum Fahrer-Wartepunkt, alle anderen zur Tribüne und spawnt bis zu acht Teamboote.
9. Jeder Fahrer steigt in sein eigenes Boot. `/wg game3 start` startet den Countdown 5..1..GO. Alternativ `/wg start`.
10. Zwei Zielüberquerungen in korrekter Richtung, mit vorherigem Abstand zur Ziellinie, ergeben das Ziel. `/wg finishround` speichert die Platzierungswertung. `/wg game3 stop` bricht ab.

## Bildschirm
`/wg screen idle` lädt die bestehende 33x9-Bildschirmkonfiguration neu und zeigt im Zentrum `TWITCH / WolfsfellTV` und `YOUTUBE / WolfsfellTVHD`. Die Rankings bleiben links/rechts. Falls Rahmen zerstört wurden, muss die Wand mit `/wg screen set` neu erstellt werden.

## Wichtige technische Grenzen
- Boote sind gegen normale Zerstörung geschützt, vor dem Start eingefroren, und nur der zugewiesene Fahrer darf einsteigen.
- Teamfarbe ist aktuell über **farbige Leuchtkontur und Teamnamen** umgesetzt. Die Bootsrümpfe selbst bleiben Vanilla. Echte farbige 3D-Bootsrümpfe benötigen ein separates Resourcepack und eine zusätzliche Modell-Integration; diese ist NICHT in diesem ZIP enthalten.
- Der Zuschauerbereich wird pluginseitig begrenzt. Admins können ihn verlassen. Es ist kein vollständiger Schutz gegen alle anderen Teleport-Plugins.
- Keine Checkpoints. Eine Überquerung zählt erst nach Verlassen der Ziellinien-Nähe; das ist kein vollständiger Abkürzungsschutz.
- Das Plugin verwendet das bestehende Kandidatensystem; es ist wichtig, dass die Kandidaten vor `/wg tp game 3` ausgewählt sind.

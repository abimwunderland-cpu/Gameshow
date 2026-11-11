# Game 4 – Zielwasser: vereinfachtes Setup

1. `/wg wand game4`: Setup-Stab erhalten.
2. Mit **Linksklick** nacheinander acht Spawn-Blöcke markieren (Spawn 1–8).
3. Mit **Rechtsklick** 80 unterschiedliche Zielblöcke markieren: Ziele 1–10 gehören zu Bahn 1, 11–20 zu Bahn 2 usw.
4. **Schleichen + Linksklick** mit Stab macht die zuletzt gesetzte Markierung dieses Stabs rückgängig.
5. In der selbstgebauten Barriere-Zuschauerbox stehen und `/wg game4 spectator` ausführen.
6. Wie gewohnt Kandidaten wählen: `/wg candidate set <Team> <Spieler>`.
7. `/wg tp game 4`: Kandidaten auf Bahnen nach der **Reihenfolge der im Plugin angelegten Teams**, Zuschauer zum gespeicherten Zuschauer-Spawn.
8. `/wg game4 start` startet den Countdown; Pfeiltreffer zählen nur auf die zehn Ziele der eigenen Bahn.

**Wichtig bei Update von älterem Game-4-Setup:** In `plugins/<Pluginname>/game4.yml` alte `spawns:`- und `targets:`-Abschnitte löschen (oder die Datei vor dem Setup sichern und entfernen). Das neue Setup nutzt nummerierte Bahnen statt Teamnamen. Den `spectator:`-Eintrag kannst du behalten.

Die Zuschauerbox muss vollständig geschlossen sein. Dieses Paket ist **Source-Code**, kein geprüfter JAR-Build. GitHub Actions soll den Maven-Build ausführen.

# Game 4 – Zielwasser (Paper 1.21.11)

Dieses Source-Update ergänzt das bestehende Gameshow-Projekt. Es ist **kein fertig getesteter JAR-Build**.

## Aufbau
1. Für jedes Team 10 Zielblöcke aufstellen. Sie bleiben als Blöcke bestehen, werden aber nach einem Treffer für dieses Team als erledigt gewertet. Mehrfachtreffer zählen nicht.
2. Eine Zuschauerbox aus Barriereblöcken über der Arena selbst bauen.
3. `/wg wand game4` – Setup-Stab holen.
4. `/wg game4 team TEAMNAME` – Team auswählen.
5. Mit dem Stab **Linksklick auf einen Block** = Kandidaten-Spawn auf dem Block speichern. **Rechtsklick auf Zielblock** = Ziel für das ausgewählte Team markieren; erneuter Rechtsklick entfernt das Ziel. Maximal 10 pro Team.
6. In der Zuschauerbox stehen und mit dem Stab **Shift+Rechtsklick** ausführen = Zuschauer-Spawn speichern.
7. Kandidaten wie gewohnt mit `/wg candidate set TEAM SPIELER` auswählen.
8. `/wg tp game 4` teleportiert alle gewählten Kandidaten zu ihren Team-Spawns, alle anderen in die Zuschauerbox.
9. `/wg game4 start` startet den 5-Sekunden-Countdown. Danach zählen Pfeiltreffer auf eigene Zielblöcke.
10. `/wg game4 stop` bricht ab; `/wg game4 reset` setzt den Spielstatus zurück, nicht die Markierungen.

## Hinweise
- Die Zielblöcke werden nicht physisch entfernt, damit ein anderer Teamdurchlauf und Neustart die Arena nicht beschädigt. Pro Team verschwinden Treffer aus der internen noch-offen-Liste.
- Zuschauer werden in der Box **teleportiert**, aber aktuell **nicht durch eine zusätzliche Plugin-Grenze festgehalten**. Die selbst gebaute Barrierebox muss vollständig geschlossen sein.
- Die Kandidaten werden vor dem Countdown nicht zusätzlich eingefroren. Bitte auf dem Startplatz warten lassen.
- Das Spielende wird automatisch erkannt, wenn alle teilnehmenden Teams ihre zehn Ziele getroffen haben. Bei Abbruch kann die Spielwertung mit `/wg finishround` gespeichert werden.
- Das Projekt enthält GitHub Actions für den Maven-Build. Es wurde hier kein vollständiger Paper-API-Build durchgeführt.

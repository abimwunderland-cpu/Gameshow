# Game 2 – Vier Antworten (Testbuild)

## Installation
1. Vorher funktionierende JAR und `plugins/WolfsfellGameshow/` sichern.
2. Diesen kompletten Maven-Quellcode in das GitHub-Repository übernehmen und GitHub Actions Build starten.
3. Nur bei grünem Build die neue JAR auf einem Testserver verwenden. Das vorhandene Buzzer-Resourcepack bleibt unverändert.
4. `questions.yml` ist eine **bestehende Datei** im Plugin-Datenordner. Die 60 Game-2-Fragen aus `src/main/resources/questions.yml` werden nur beim ersten Erstellen kopiert. Für einen vorhandenen Server die neuen Game-2-Fragen manuell aus der ZIP in die bestehende `plugins/WolfsfellGameshow/questions.yml` übernehmen. Nicht blind die alte Datei löschen, falls eigene Fragen enthalten sind.

## Testablauf
```
/wg game 2
/wg studio lock
/wg select on
```
Spieler wählen lassen, dann:
```
/wg select off
/wg candidates
/wg start
```
Kandidaten werden zu ihren Team-Auswahlpositionen teleportiert und erhalten A/B/C/D in Hotbar-Slots 1-4. Rechtsklick mit einem Item speichert genau eine Antwort. Die Mitte des 33x9-Displays zeigt Frage, vier Optionen, Countdown und Antwortzähler. 7 Sekunden Antwortzeit, 3 Sekunden Auflösung, 15 Fragen. Richtige Antwort +1 Teamquizpunkt und +1 persönlicher Punkt. Anschließend `/wg finishround` zur Platzierungswertung.

## Testfälle
- Nicht-OP und OP können jeweils antworten; doppelte Antwort wird ignoriert.
- Items lassen sich nicht verschieben oder droppen.
- `/wg stop` stellt Inventare wieder her; auch nach normalem Spielende.
- Bei Crash bleiben Inventar-Backups in `game2-inventory-backups.yml` gespeichert und werden beim nächsten Login wiederhergestellt.
- Game 1 muss unverändert weiter funktionieren.

## Bekannte Grenzen
- 60 Game-2-Fragen statt der geplanten 120; davon 30 Minecraft und 30 Allgemeinwissen. Keine automatische Schwierigkeitsverteilung 4/7/4.
- Der 2-Sekunden-Frage-Intro-Screen ist noch nicht separat umgesetzt; Frage startet direkt nach dem Übergang.
- Ein Spieler, der während des laufenden Quiz reconnectet, erhält sein gesichertes Inventar zurück, nicht automatisch neue Antwort-Items.
- Ein vollständiger Maven-/Paper-Multiplayer-Build konnte hier nicht ausgeführt werden. GitHub Actions und ein Testserver sind verpflichtend.

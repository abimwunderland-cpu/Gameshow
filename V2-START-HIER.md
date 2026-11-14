# Wolfsfell Gameshow V2 – Update für Paper 1.21.11

Diese ZIP enthält den **Quellcode**, nicht die kompilierte JAR. Die JAR lässt sich mit GitHub Actions aus `.github/workflows/build.yml` erstellen. Erwarte `target/WolfsfellGameshow-0.2.0.jar` im Workflow-Artifakt `WolfsfellGameshow-JAR`.

## Vor dem Sonntagstest

1. **Sichere** den gesamten `plugins/WolfsfellGameshow/`-Ordner, besonders `state.yml`, `game3-race.yml`, `game4.yml` und deine Cage-/Studio-Setups.
2. Ersetze im GitHub-Repository den **vollständigen Inhalt** mit dem entpackten Source-Projekt (inkl. `pom.xml`, `.github/workflows/build.yml` und `src`).
3. Starte die GitHub Action `Build WolfsfellGameshow` und lade die neue `WolfsfellGameshow-0.2.0.jar` herunter. Es muss ein **grüner Maven-Build** sein, bevor du sie auf dem Server nutzt.
4. **Die neuen Fragenpools installieren sich beim ersten V2-Start einmalig automatisch**. Die vorherigen Dateien `game2-questions.yml` und `questions-game5.yml` werden dabei als `*.vor-v2.bak` gesichert. Spätere eigene Bearbeitungen bleiben bei Neustarts erhalten. Sicherung des gesamten Plugin-Ordners trotzdem empfohlen.
5. Neues V2-Resourcepack öffentlich direkt hosten (z. B. MC-Packs), Link + **neuen SHA-1-Hash** in die Serverkonfiguration übernehmen, Server neu starten. Alte Pack-Hashes zeigen noch alte Texturen und Sounds an.
6. Teams für die neue Show mit `/wg reset scores` zurücksetzen (setzt **Punkte** zurück, nicht deine Regionen), Teamliste und Cages kontrollieren.

## Was neu ist

- Bis zu 16 Teams (1–3 Spieler); Spielernamen über TAB für Team-/Kandidatenbefehle.
- **Leistungswertung**: Spiele 1–5 je 0–100 Punkte. Game 1 = 10 Punkte pro richtiger Antwort (10 Fragen), Game 2 = 100 × richtige Antworten / 15, Game 3 = Rennleistung, Game 4 = 10 Punkte je Target (10 Ziele), Game 5 = 100 × Schätzpunkte / 30. Ungewertete oder falsche Antworten führen nicht mehr zu Platzierungs-Gratisscores.
- **Game 5**: 12 Sekunden Antwortzeit, 10 Sekunden Auflösung. Geheime Texteingaben werden beim Empfang erfasst und vor der Auswertung in der Hauptthread-Warteschlange verarbeitet. Im Chat erscheint privat die Bestätigung `GESPEICHERT`.
- Game 1: 29 neue echte Minecraft-Itemsprites in `src/main/resources/game1/` (Vanilla-Assets, ursprüngliche Texturen), neuer Gegenstandspool.
- Game 2: 46 neue mittlere/schwere Minecraft-Fragen, größere Frage auf der Map-Wand und Resourcepack-Antworticons A/B/C/D.
- Game 3: Staffellauf mit 3 Runden pro Team; Solo A–A–A, Duo A–B–A, Trio A–B–C. 16 gleichzeitig aktive Teamboote. Bereits markierte Boots-Spawns 1–8 bleiben erhalten; für >8 Teams die Starts **9–16** zusätzlich mit `/wg wand game3` markieren. Fahrer-Wartepunkt und Tribüne müssen gesetzt sein. Die Zeit läuft bei Teamwechseln durch.
- Game 4: bis zu 16 Teams in zwei Durchgängen à 8 Bahnen. Fehlende Target-Blöcke werden bei Spielstart aus gespeicherten Positionen repariert. Nach Durchgang 1: `/wg tp game 4 2`, dann `/wg game4 start` (oder `/wg start`).
- Originale, dezente Musikschleifen für alle 9 Spiele im neuen Resourcepack (`settings.music-volume: 0.15`, 0 = aus). Das alte Sounddesign bleibt.
- Game 8: dauerhaft sichtbarer Punktestand beider Teams in der Actionbar und deutliches Kistenergebnis.
- Games 6–9, Cages und gesammelte Kit-UHC-Vorteile sind im Projekt enthalten.

## Moderator: neue Score-Befehle

```text
/wg score add Team1 10
/wg score remove Team1 10
/wg score set Team1 120
/wg score game 4 Team1 100
/wg score history Team1
/wg score undo
/wg rank
```

`/wg score game` nimmt **exakte** Punkte von 0 bis 100 ohne Umrechnung auf Trefferzahl an. `set` setzt den **gesamten** Punktestand (nach bereits gewerteten Spielen); `add/remove` ändern nur den Stand und `undo` nimmt den letzten manuellen Eingriff zurück. Diese Korrekturen überleben Serverneustarts, aber die Undo-Historie nur bis zum Neustart.

## Wichtige Verifikation

Diese Version ist nicht auf einem laufenden Paper 1.21.11-Server getestet. Der erfolgreiche GitHub-Actions-Maven-Build und ein vollständiger Probedurchlauf müssen vor dem Event erfolgen. Ohne Maven-/Paper-API-Zugang hier kann die JAR nicht belastbar kompiliert werden. Bei einem roten Build die konkreten Fehlerzeilen schicken; nicht eine ältere JAR als V2 ausgeben.

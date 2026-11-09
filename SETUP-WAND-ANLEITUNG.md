# Wolfsfell Gameshow – Setup-Wand

## Teamverwaltung
- `/wg team create Team1`
- `/wg team delete Team1` – entfernt Team und zugehörige Punkte, Auswahl, Käfigregion und Neonmarkierungen
- `/wg team join Team1 Spielername`
- `/wg team color Team1 BLUE` – färbt automatisch die Lederbrustplatte der Online-Teammitglieder
- Farben: PURPLE, BLUE, RED, GREEN, YELLOW, ORANGE, PINK, CYAN

## Regionen mit Wand markieren
1. `/wg wand region` – gibt einen Blaze-Rod als Setup-Wand
2. Linksklick auf Block: POS1; Rechtsklick auf Block: POS2
3. `/wg region save cage:Team1` – speichert den Teamkäfig
4. `/wg region save selection:Team1` – speichert den Auswahlbereich für Team 1; während `/wg select on` wird ein Spieler nach 20 Ticks darin registriert
5. Wiederholen für Team2–Team8

## Teampositionen
- Stelle dich an die Teamposition und führe `/wg set team:Team1` aus.
- Allgemeine Orte: `/wg set studio`, `/wg set race-start`, `/wg set race-finish`, `/wg set parkour-start`, `/wg set parkour-finish`.
- `/wg tp team:Team1` teleportiert dich zu einer gespeicherten Position.

## Neon-Blöcke
1. `/wg wand neon Team1` – Neon-Wand erhalten
2. Rechtsklick auf einen Block: farbige, voll beleuchtete BlockDisplay-Überlagerung mit Glow-Kontur setzen
3. Noch einmal rechtsklicken: entfernen

**Technische Grenze:** BlockDisplays mit Glow und maximaler Display-Helligkeit sehen hell aus, erzeugen aber *kein echtes Umgebungslicht*. Für Licht auf benachbarten Blöcken sind zusätzliche Light-Blocks oder ein Shader erforderlich. Der Neon-Effekt ist zunächst eine visuelle Überlagerung.

## Installation
Quellcode auf GitHub hochladen, GitHub Actions Build abwarten, JAR aus Artifacts herunterladen und im Paper-1.21.11-Server unter `plugins/` installieren. Das Plugin wurde hier noch nicht erfolgreich kompiliert oder im Spiel getestet.

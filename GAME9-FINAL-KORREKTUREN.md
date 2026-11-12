# Gameshow Final-Korrekturen (Games 1–9)

## Installation
Alle Dateien aus diesem Source-Archiv im GitHub-Repository ersetzen, dann den GitHub-Actions-Workflow laufen lassen. Vorher das bestehende `plugins/WolfsfellGameshow/state.yml` sichern und nicht mit einer leeren Datei überschreiben.

## Zuschauertribüne in Game 9
`/wg set game9-spectator` ist jetzt auch **über / innerhalb der X/Z-Arena** erlaubt. Du kannst die Zuschauer mit einer Barriere-Plattform über der Arena unterbringen. Andere Spieler werden zum Zuschauerpunkt teleportiert. Nicht-OP-Spieler werden auf Spectator gestellt.

## Game 9 PvP
Beim Start der Kit-UHC-Runde wird PvP für die Welt mit den Arenaspawns aktiviert und nach Kampfende auf den vorherigen Wert zurückgestellt. Auch `server.properties` sollte `pvp=true` haben. Andere Schutzplugins (z. B. WorldGuard) können PvP weiterhin separat blockieren – prüfe deren Flags im Arenabereich.

## Rücktransport nach jedem Game (außer 9)
Nach Abschluss von Games 1–8 werden die Spieler nach kurzer Siegeranzeige (3 Sekunden) zu `team:<teamname>` zurückteleportiert. Diese Punkte müssen eingerichtet sein, z. B. `/wg set team:Rot` am Cage von Team Rot. Game 9 behält das Siegerteam in der Arena. Abgebrochene Runden zählen nicht als abgeschlossen.

## Gameshow Hologramm-Board
An gewünschter Stelle stehen und `/wg board set` eingeben. Der komplette Spielplan (alle neun Games + eine kurze Erklärung) erscheint als Text-Hologramm. Beendete Games sind durchgestrichen, das nächste offene Spiel leuchtet in grüner Schrift. Weitere Befehle: `/wg board refresh` und `/wg board remove`. Die Position und bereits abgeschlossenen Games werden in der state.yml gespeichert. Wird ein Spiel erneut getestet, zeigt es weiter 'abgeschlossen', bis `/wg reset scores` ausgeführt wird.

## Game 8 Mysteries
Jede gezogene Zahl erscheint allen Spielern groß als Title und schwebt kurzfristig über der geöffneten Kiste. +1/+2 sind grün, +0 weiß und -1/-2 rot. Die laufende Gesamtsumme bleibt im Chat.

## Hinweise
Sourcecode-Paket, kein bestätigter Ingame-Test. Die GitHub Actions müssen noch den Build bestätigen. Für umfangreiche Tests vorab World- und Config-Backup erstellen.

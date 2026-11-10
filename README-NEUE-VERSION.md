# Gameshow – Zwischenstand 10.11.2026

Dies ist ein **inkrementeller Quellcode-Patch**, keine vollständige Umsetzung aller neun Spiele.

## Neu im Code
- Physischer Minecraft-Knopf pro Team: Knopf ansehen, `/wg buzzer set <Team>`; nur der gewählte Teamspieler darf ihn verwenden.
- Spiel 1: 5 Sekunden Chat-Antwortzeit, ein Buzzer-Versuch pro Spieler und Frage, 1 Punkt pro richtiger Antwort, maximal 10 Fragen.
- Spiel 2: 7 Sekunden pro Frage, maximal 10 Fragen, A/B/C/D im Chat.
- Spiel 5: 7 Sekunden, maximal 10 Fragen; alle gleich nahen besten Schätzungen erhalten 1 Punkt, Punktlandungen 2.
- Die markierten Buzzer werden in `state.yml` gespeichert.

## Noch NICHT implementiert
- 9×9 Studiobildschirm mit Bildern und Enthüllung, physische 4-Antworten-Displays.
- Feste Teamnamen 1–8 mit Datenmigration. Bestehende Teams bleiben aus Sicherheitsgründen unverändert.
- Grand Prix mit 3 Runden, markierten Start-/Ziellinien und ohne Checkpoints.
- Zielwasser mit automatisch verschwindenden Zielen, Wand und Zeitwertung.
- Mystery-Truhen-Wand, alle Arena-Regionsbegrenzungen und automatische Teleports.
- BasketballCraft-Integration, vollständiger KitUHC-Shop und Kampfsystem.

**Nicht auf einem Produktivserver ohne Backup einsetzen.** Quellcode wurde hier nicht gegen Paper kompiliert oder in Minecraft getestet.

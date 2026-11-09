# Wolfsfell Gameshow – Entwicklungspaket

**WICHTIG:** Quellcode, keine geprüfte fertige JAR. Kompilierung gegen Paper 1.21.11 konnte hier nicht durchgeführt werden (keine Maven-/Paper-Abhängigkeiten verfügbar). Features sind ein Entwicklungsstand und müssen serverseitig getestet werden.

## Installation
1. Repository mit Java 21 und Maven bauen: `mvn clean package`. GitHub Actions Workflow liegt bei.
2. Erzeugte JAR in `plugins/` eines Paper-1.21.11-Servers legen.
3. Das separate Ressourcenpack ZIP in Minecraft laden oder serverseitig anbieten.
4. `/wg help` aufrufen.

## Befehle
- `/wg team create Team1`, `/wg team join Team1 Spieler`, `/wg team list`
- `/wg game 1` bis `/wg game 9`, `/wg start`, `/wg stop`, `/wg status`
- `/wg set selection`, `/wg select on` – ausgewählte Kapitäne am Studio-Platz registrieren
- `/wg buzz` – beim Itemquiz buzzern, dann im Chat antworten
- `/wg next` – nächste Frage, `/wg finishround` – Rohpunkte als Vorrundenwertung buchen
- `/wg target add Team1` – anvisierten Zielblock für Team markieren (10 pro Team)
- `/wg checkpoint add`, `/wg checkpoint clear`, `/wg set race-finish`
- `/wg raw Team1 3 42`, `/wg rank`, `/wg qualify`
- `/wg basket Team1 5`, `/wg set parkour-finish`, `/wg chest add`, `/wg chest retire Team1`
- `/wg shop` – KitUHC-Kauf-GUI (Einkäufe werden erfasst, noch keine automatische Kit-Ausgabe)

## Stand und Grenzen
- Tab: Teamzugehörigkeit, Twitch/YouTube-Eigenwerbung; Sidebar: Top-5-Teams, Top-5-Spieler und eigener Rang.
- Itemquiz: Chat-Buzzer und Fragenpool; **das eigentliche Item-Enthüllungsdisplay ist noch nicht implementiert**.
- Vier Antworten: Antwort A/B/C/D im Chat, gemischte Positionen, Auflösung.
- Schätz mal: Chat-Schätzungen, Top 3 erhalten 3/2/1 Punkte.
- Rennen: Checkpoint- und Zielerkennung; **teamfarbige Custom-Boote fehlen**.
- Zielwasser: Teamgebundene Treffer; **Zielblöcke verschwinden derzeit nur logisch und werden nicht optisch entfernt; serverseitige Pfeilstreuung fehlt**.
- Basketball: manuelle Punkteübernahme, **keine BasketballCraft-API-Integration**.
- Mystery: 50 Truhen, Zeitlimit, freiwilliger Ausstieg.
- KitUHC: Punkte-Shop-Buchung, **keine vollständige Kampf- und Kit-Automation**.
- Ressourcenpack: **12 eigens synthetisierte kurze OGG-Sounds**, Pack-Icon; **keine fertigen Boot-Modelle, kein Bildschirmsystem**.
- `questions.yml` enthält einen bearbeitbaren Startpool. Einfache Eingabe neuer Fragen erfolgt über die Datei; **Ingame-Frageneditor fehlt**.

Dies ist kein vollständig spielbares Gameshow-Release. Offene Punkte sind bewusst benannt, statt Funktionen vorzutäuschen.

# Game 2 – Minecraft Expertenquiz (Finale)

81 ausschließlich Minecraft-Fragen, 15 pro Show: 4 leichte, 7 mittlere, 4 schwere. Die Fragen sind für erfahrene Spieler geschrieben.

**7 Sekunden** Antwortzeit, **6 Sekunden** Auflösung, A/B/C/D per Rechtsklick in der Hotbar.

## WICHTIG – vorhandene Fragebank auf dem Server
Die Plugin-Datei `plugins/WolfsfellGameshow/game2-questions.yml` wird bei Updates absichtlich nicht überschrieben.
Die neue `src/main/resources/game2-questions.yml` aus diesem ZIP muss auf dem Server die alte Datei ersetzen.
Alternativ die alte `game2-questions.yml` im Plugin-Datenordner vor dem Serverstart umbenennen, z. B. in `game2-questions-alt.yml`. Dann legt das Plugin automatisch die neue Datei an.
**Nicht `state.yml` löschen**, sonst gehen Teams und Punktestände verloren.

Nach dem Serverstart: `/wg game 2`, Kandidaten auswählen, `/wg start`. Nach 15 Fragen `/wg finishround`.

Der Schwierigkeitsgrad wird pro Frage anhand `[LEICHT]`, `[MITTEL]`, `[SCHWER]` festgelegt. Der Fragenpool wird zufällig innerhalb jeder Stufe gemischt.

Hinweis: Quellcodepaket; Build und Server-Test ausstehend.

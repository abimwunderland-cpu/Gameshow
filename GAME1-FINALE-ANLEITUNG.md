# Game 1: Items + Buzzer-Farben + automatischer Teleport

## Änderungen
- `/wg start` teleportiert die bereits gewählten, online befindlichen Kandidaten automatisch zu `selection:TeamX`. Falls kein Punkt gesetzt ist, sucht das Plugin einen sicheren Standort in der gespeicherten Auswahlregion.
- Für präzise Positionen: Als OP in die Auswahlbox stellen und `/wg set selection:Team1` (Teamnamen entsprechend ersetzen). Das ist besonders bei kleinen/ungleichmäßigen Boxen empfehlenswert.
- 30 vorhandene Itembilder statt nur 10 werden als zufälliger Pool für 10 Runden verwendet. Die 20 zuvor ungenutzten Bilder sind jetzt aktiviert.
- Jeder gespeicherte Buzzer erhält eine sichtbare farbige BlockDisplay-Kappe nach Teamfarbe. Die echten Knöpfe bleiben die Interaktionsfläche. Die Kappen werden beim Serverstart und bei Änderungen aktualisiert.
- Bestehende Teams, Regionen, Punkte und Buzzer werden nicht zurückgesetzt. `state.yml` auf dem Server behalten.

## Wichtig
Die Bilder sind die im Projekt vorhandenen PNG-Dateien; sie sind **nicht** als offizielle Vanilla-Texturen verifiziert. Die farbigen Kappen sind eine Plugin-Visualisierung, kein 8-faches Custom-Model-Resourcepack. Ein echter 3D-Button-Pack ist ein separater Design-Schritt.

## Test
`/wg game 1`, `/wg studio lock`, `/wg select on`, Spieler auswählen lassen, `/wg select off`, `/wg candidates`, `/wg start`.

Die Java-Quellen sind nicht auf einem Paper-Server getestet. GitHub Actions muss das Projekt kompilieren.

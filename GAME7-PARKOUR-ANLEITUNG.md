# Wolfsfell Gameshow – Finale Update / Game 7

## Game 6 – Wortduell
- Jede Frage wird nun neben dem 33×9-Screen für alle im normalen Chat angezeigt.
- Dezente Chime-/Pling-/Bell-/Amethyst-Sounds für Frage, Start, überlebte Runde und Abschluss.
- Wortduell vergibt nur den gespeicherten Netherite-Hosen-Vorteil (+1 Vorteilspunkt), keine Vorrunden-Rangpunkte.
- Nach dem Finaleinzug zeigt die Sidebar und die 33×9-Rangliste nur Finalteams/Vorteilspunkte, nicht die alten 100/80 usw. Vorrundenpunkte bleiben für die Ermittlung der Finalisten gespeichert.

## Game 7 – Parkour (max. 6 Minuten)
1. Parkourstrecke wie gewünscht selbst bauen.
2. `/wg wand game7` zum Markieren erhalten.
3. Mit dem Stab **LINKSKLICK** auf den Boden-Startblock: Startpunkt direkt oberhalb wird gespeichert.
4. Mit dem Stab **RECHTSKLICK** auf das erste Ende der Ziellinie: Ziel A.
5. Mit dem Stab **SHIFT + RECHTSKLICK** auf das zweite Ende der Ziellinie: Ziel B.
   - Die Ziellinie ist die durch diese beiden Endpunkte begrenzte Fläche, inkl. leichter Treffertoleranz und vertikal 2 Blöcken über Boden.
6. `/wg game 7`
7. `/wg select on`, pro Finalteam einen Kandidaten auswählen, danach `/wg select off`.
   Alternativ: `/wg candidate set <Team> <Spieler>`.
8. `/wg game7 tp` teleportiert beide Finalkandidaten nebeneinander zum Start.
9. `/wg game7 start` teleportiert erneut, setzt 5-Sekunden-Countdown und startet die 6-Minuten-Uhr bei „LOS“.
10. Der erste Teilnehmer, der die Ziellinie berührt, gewinnt. Ende nach 6 Minuten ohne Sieger: kein Vorteil.

## Kommandos
- `/wg wand game7`: Markierungsstab
- `/wg game7 tp`: Kandidaten zur Arena
- `/wg game7 start`: automatischer Countdown und Rennen
- `/wg game7 stop`: Rennen abbrechen, ohne Vorteil
- `/wg tp game 7`: alternativer Arena-Teleport

## KitUHC-Vorteil
Der Sieger erhält einmalig einen gespeicherten **Netherite-Schwert / Schärfe I**-Vorteil (+1 Vorteilspunkt). Dieser wird erst beim späteren KitUHC-Game tatsächlich als Gegenstand ausgeteilt. Der Status wird in `state.yml` unter `sword-rewards` gespeichert.

**WICHTIG:** Vor dem Überschreiben `plugins/WolfsfellGameshow/state.yml` sichern. ZIP enthält Quellen, keine fertig kompilierte JAR. GitHub Actions muss erfolgreich bauen.

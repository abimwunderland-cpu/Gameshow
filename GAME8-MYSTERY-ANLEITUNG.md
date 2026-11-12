# Game 8 – Mystery-Chests

## Einrichten
- Baue 50 normale oder Trapped-Chests (am besten Einzelkisten).
- `/wg wand game8`: Rechtsklick auf Kiste = markieren, Linksklick = Markierung entfernen.
- `/wg set game8-start`: Arena-Startpunkt für beide Finalisten (in Arena stehen).
- `/wg game 8` auswählen; `/wg select on`, pro Finalteam einen Spieler wählen, `/wg select off`.
- `/wg game8 tp` teleportiert die zwei Kandidaten.
- `/wg game8 start` startet (und mischt automatisch neu).

## Spiel
- 50 Kisten, versteckte Werte -2,-1,0,+1,+2; von jedem Wert 10 Kisten, zufällig verteilt.
- Die Reihenfolge wird bei jedem neuen Spiel zufällig bestimmt; nur der aktuelle Kandidat kann eine unbenutzte Kiste öffnen.
- Der Kisteninhalt bleibt geschlossen; der ermittelte Wert erscheint im Chat und zählt nur im Game 8, nicht in den Vorrundenpunkten.
- Jeder aktive Kandidat kann mit `/wg game8 raus` freiwillig aussteigen (unwiderruflich). Moderator: `/wg game8 retire <Team>`.
- Der Gegner kann nach dem Ausstieg allein weiterspielen. Sind alle ausgestiegen oder 50 Kisten geöffnet, endet das Spiel.
- Höchster Spielstand gewinnt den gespeicherten KitUHC-Vorteil: 3 Heilungstränke (noch keine sofortigen Items). Bei Gleichstand erhalten ihn beide.
- `/wg game8 stop` bricht ohne Vorteil ab. `/wg game8 status` zeigt Markierungen/Status.

**Hinweis:** Der Start würfelt die Kistenwerte neu. Bereits gewonnene KitUHC-Vorteile werden nicht gelöscht. Später beim KitUHC-Bau wird der gespeicherte Trankvorteil tatsächlich ausgegeben.

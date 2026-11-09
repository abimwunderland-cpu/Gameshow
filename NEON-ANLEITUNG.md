# Wolfsfell Gameshow – Neon-Regionen

## Einmalig nach Update
1. Alte JAR ersetzen, Server vollständig neu starten.
2. `/wg neon cleanup` entfernt die alten, vom Plugin erkannten leuchtenden BlockDisplays an den zuvor gespeicherten Neonpositionen. Es entfernt **nicht** pauschal alle BlockDisplays deiner Map.
3. Verwende **nicht** die alte Neon-Wand; das neue System nutzt nur die Regions-Wand.

## Team Huan markieren
1. `/wg wand region`
2. Linksklick auf eine untere Ecke der gewünschten Neon-Umrandung: POS1.
3. Rechtsklick auf die diagonal gegenüberliegende obere Ecke: POS2.
4. `/wg neon save Huan` – vier lange, dünne Linien auf Höhe der oberen Auswahlkante, plus Light-Blocks in freien Luftfeldern.
5. `/wg neon clear Huan` – Neonregion wieder entfernen.

## Hinweise
- Die Markierung definiert ein Rechteck in der X/Z-Ebene. Die höhere der beiden Y-Koordinaten bestimmt die Höhe der Neonstreifen.
- Für den cleansten Effekt nur die Kante der Cage-Oberseite markieren, nicht den gesamten Raum.
- Lichtblöcke erzeugen weißes Licht. Die Teamfarbe entsteht durch die Glasstreifen und die Glow-Kontur.
- Die alten, bereits erzeugten Displays ohne Plugin-Kennzeichnung werden nur nahe zuvor gespeicherter Neonmarkierungen gesucht. Bei Fremd-Displays aus farbigem Glas/Beton mit Glow in unmittelbarer Nähe ist eine Verwechslung möglich; vorher Backup erstellen.
- Kein vollständiger Paper-Build wurde lokal ausgeführt. GitHub Actions muss die Kompilierung bestätigen.

# Cage Hotfix

## Nach dem Upload und GitHub-Actions-Build

`/wg cages status` — Zeigt für jedes Team, ob Cage-Region und sicherer Teleportpunkt vorhanden sind.

`/wg cages tp` — Alle Online-Teammitglieder sofort zurück in ihre Cages teleportieren, Auswahl abschalten und Cage-Sperre aktivieren.

Für jeden Cage weiterhin `/wg wand region`, Linksklick auf Ecke 1, Rechtsklick auf Ecke 2 und `/wg region save cage:Team1` usw. Die Region muss innen mindestens zwei Luftblöcke übereinander und einen soliden Boden enthalten. Ein separates `/wg set team:Team1` ist nun optional; der Hotfix sucht sonst einen sicheren Spawn innerhalb der Region.

Das automatische Zurückteleportieren nach beendeten Games 1–8 ist ebenfalls aktualisiert. Game 9 bleibt ausgenommen.

Nach der Spielerwahl sind Teammitglieder nicht mehr allein deshalb unbeschränkt beweglich, weil sie früher einmal ausgewählt wurden.

**Achtung:** OPs bzw. Spieler mit `wg.admin` sind weiterhin als Moderatoren von der Bewegungssperre ausgenommen. Teste den Cage-Schutz mit einem normalen Spieler ohne diese Permission.

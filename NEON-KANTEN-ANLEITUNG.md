# Wolfsfell Gameshow – Neon-Kanten und Auswahlplätze

## Team Huan
1. `/wg wand region`
2. Linksklick auf die untere Ecke des Käfigs (POS1), Rechtsklick auf die gegenüberliegende obere Ecke (POS2).
3. `/wg neon save Huan` – sichtbare obere Außenkanten zusammenhängender Blöcke bekommen dünne Streifen; interne Blocknähte bleiben frei.
4. Markiere den mittleren Auswahlplatz mit POS1/POS2 und speichere ihn: `/wg region save selection:huan`.
5. `/wg neon selection Huan` – auch die sichtbaren Blockkanten des Auswahlplatzes werden beleuchtet.

## Löschen
`/wg neon clear Huan` entfernt Käfig-Neon; `/wg neon clear selection:Huan` entfernt Auswahlplatz-Neon.

## Einschränkungen
Vanilla Minecraft bietet keine echte farbige Lichtausbreitung. Die dünnen BlockDisplays stellen die Teamfarbe dar; unsichtbare LIGHT-Blöcke beleuchten weiß. Es werden höchstens 160 zusammenhängende Streifen je Region erzeugt, bei komplexen Regionen werden weitere Kanten ausgelassen. Region max. 49 × 17 × 49 Blöcke. Vor dem Update Welt-Backup anlegen. Projekt muss noch mit Paper API kompiliert und in Minecraft getestet werden.

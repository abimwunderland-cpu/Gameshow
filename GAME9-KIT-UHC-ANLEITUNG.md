# Game 9 – Kit-UHC 1vs1 · Wolfsfell Gameshow

## Arena einmalig markieren

1. `/wg wand game9` gibt die Setup-Wand.
2. Linksklick auf die erste Eckposition der Arena (A).
3. Rechtsklick auf die diagonal gegenüberliegende Eckposition (B).
   **Die Markierung schützt die ganze vertikale Spalte** zwischen diesen X/Z-Ecken (vom Weltboden bis zur Baugrenze). Zuschauertribüne muss außerhalb der Spalte liegen.
4. Stell dich auf Spawn 1 und führe `/wg set game9-spawn1` aus.
5. Stell dich auf Spawn 2 und führe `/wg set game9-spawn2` aus.
6. Stell dich auf die Zuschauertribüne und führe `/wg set game9-spectator` aus.

## Finale starten

Nach Game 5 `/wg finishround` ausführen. Finalisten werden automatisch aus der Vorrundenwertung gewählt; alternativ vorher `/wg finalist TeamA TeamB`.

- `/wg game 9` – Spiel wählen (setzt die Kandidatenauswahl zurück).
- `/wg select on` – beide Finalteams schicken jeweils **einen** Spieler in die Selection-Box; alternativ `/wg candidate set <Team> <Spieler>`.
- `/wg select off` – Auswahl abschließen.
- `/wg game9 tp` – Kämpfer zu ihren Startpositionen, übrige Online-Spieler auf die Zuschauertribüne.
- `/wg game9 start` – Kit-Ausgabe und 5-Sekunden-Countdown starten.
- `/wg game9 status` – Laufstatus und letztes Resultat.
- `/wg game9 stop` – Kampf abbrechen, keine Siegerteam-Wertung.

## Kit

- Diamantrüstung, jedes Teil Schutz I
- Diamantschwert Schärfe I, Diamantaxt, Diamantspitzhacke Effizienz II
- Bogen Stärke I, 16 Pfeile, Schild
- 5 goldene Äpfel, 16 Steak
- 64 Cobblestone, 8 Cobwebs, 2 Wassereimer, 1 Lavaeimer

Frühere Finalvorteile werden **automatisch** beim Kit verteilt:
- Game 6: Netherite-Hose Schutz I, Haltbarkeit I (ersetzt Diamanthose)
- Game 7: Netherite-Schwert Schärfe I (ersetzt Diamantschwert)
- Game 8: 3 Tränke Sofortheilung II (zusätzlich)

Die Zahl der Vorteile ist nicht das Kampfergebnis: **der erste Tod (oder Disconnect) entscheidet das Finale**. Kein Zeitlimit.

## Arenaschutz

- Ursprüngliche Arenablöcke können während des Kampfes nicht abgebaut werden.
- Nur während des laufenden Kampfes durch die Finalkämpfer platzierte Blöcke können abgebaut werden.
- Cobwebs/Blöcke/Wasser/Lava sind gestattet, Flüssigkeiten können den Arenabereich nicht verlassen.
- Temporär platzierte Blöcke und erfasste Flüssigkeiten werden nach dem Finale entfernt. **Die ursprüngliche Arena wird nicht als Ganzes zurückgesetzt.**
- Arena-Ausbruch durch Laufen oder Bauen außerhalb der markierten X/Z-Grenze wird unterbunden.
- Explosionen schützen die Originalblöcke, Feuer wird innerhalb der Arena unterdrückt.

## Sieg und Inventare

Nach dem Tod bleiben Sieger und Siegerehrung in der Arena. Alle Mitglieder des Gewinnerteams werden zur Siegerposition gebracht. Auf allen Bildschirmen erscheint das Gewinnerteam, Sounds und Feuerwerk werden ausgelöst. Der Gegner wird zum Zuschauer.

Vor dem Kit-Wechsel wird das aktuelle Inventar einschließlich Rüstung und Offhand gesichert und nach dem Kampf wiederhergestellt. Nach einem **Serverabsturz** während des Duells kann diese nur im RAM gehaltene Sicherung verloren gehen: vor dem Test mit wertvollen Items Inventare leeren oder ein externes Backup anlegen.

## Testhinweise

Die Source enthält die neuen `GameNineUhc.java` und das aktualisierte `GameshowPlugin.java`; der Build ist **nicht** mit einer Paper-API kompiliert worden und benötigt GitHub Actions. Bitte den Actions-Build prüfen und die JAR vor produktiven Events testen.

# Game 5 – Schätz mal! Fix

- Antwortzeit jetzt 8 Sekunden.
- Auflösung jetzt 10 Sekunden sichtbar (vorher 5 Sekunden).
- Mehr Soundeffekte: Start, Frage, letzte 3 Sekunden, abgegebene Antwort, Auflösung, nächste Frage.
- Kritischer Doppelablauf behoben: Der allgemeine Tick-Handler löst Game 5 nicht mehr zusätzlich zur eigentlichen Game-5-Routine auf. Das war die Ursache der doppelten Lösungen, überschneidenden Fragen und falschen Fragen-Reihenfolge.
- Der allgemeine `/wg next` Befehl kann Game 5 nicht mehr parallel um eine Frage weiterschalten.
- Doppelte identische Fragetexte werden beim Laden aus questions-game5.yml ignoriert.
- Alle anderen Spiele bleiben erhalten.

Hinweis: Bitte erst GitHub Actions erfolgreich bauen lassen und dann auf einer Testinstanz prüfen.

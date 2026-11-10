# Game 1 – Nicht-OP-Buzzer und Tab-Completion

- Physischer Buzzer wird bei EventPriority.LOWEST ausgewertet, auch bei bereits abgebrochenen Interaktionen.
- Registrierte Buzzer unterdrücken die Vanilla-Knopfaktion, ohne OP-Berechtigung zu benötigen.
- Tab-Vervollständigung für `/wg candidate set <Team> <Online-Spieler aus Team>` und `/wg buzzer set <Team>`.
- **Wichtig:** Wenn ein Schutz-Plugin das Interaktionsereignis vollständig verhindert, kann das Gameshow-Plugin es nicht empfangen. Teste `spawn-protection=0` in `server.properties` und starte den Server neu; prüfe außerdem WorldGuard/GriefPrevention-Interaktionsrechte.
- Bestehende Teams, Regionen, Buzzer und `state.yml` bleiben unverändert.

Test: `/wg game 1`, `/wg candidate set Team2 <TAB>`, `/wg start`, danach als Nicht-OP Team2-Knopf rechtsklicken.

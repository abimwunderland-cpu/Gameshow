# Game 6 – Wortduell und Finaleinzug

Nach Game 5: `/wg finishround` (bestimmt Finalteams und setzt übrige Teams in Spectator). Bei Punktegleichstand um Platz 2 zuerst `/wg finalist TeamA TeamB`, dann `/wg finishround`. Für eine manuelle Auswahl ebenfalls zuerst `/wg finalist`.

Moderator kündigt Spiel an: `/wg game 6`, dann `/wg select on`; je ein Finalist besetzt die bestehende Selected-Position. Danach `/wg select off`, `/wg game6 start` oder `/wg start`.

Pro Frage 3 Sekunden Lesezeit, danach 13,12,...,1 Sekunden Antwortzeit. Korrekturen bei ungültigen Eingaben sind möglich, gültige Antworten werden gelockt. Antwortchat wird nicht öffentlich angezeigt; Ops sehen Antworten. Kein Spieler darf eine Antwort wiederverwenden. Sobald eine oder beide Seiten scheitern, ist Game 6 beendet. Bei gleichzeitigem Scheitern oder beiden erfolgreichen Spielern in Runde 13 bekommen beide Teams den Leggings-Vorteil (+1 gespeicherter Vorteilspunkt). Ausrüstung wird noch nicht direkt ausgegeben.

`/t <Nachricht>` privater Teamchat, `/tc on` / `/tc off` dauerhafter Teamchat. Operatoren sehen Teamchat.

`/wg game6 reload` lädt `plugins/WolfsfellGameshow/questions-game6.yml` neu. Bitte Antwortlisten prüfen und bei Bedarf anpassen, bevor ihr auf einem Liveserver startet.

Der Build wurde in dieser Umgebung nicht ausgeführt. Testet zuerst auf einem Testserver und erstellt vor Installation ein Backup eurer `state.yml`.

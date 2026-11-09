# Wolfsfell Gameshow – Glow-Update

- Teammitglieder erhalten einen permanenten Glowing-Effekt in ihrer Scoreboard-Teamfarbe. Der Effekt wird alle zwei Sekunden aufgefrischt, auch nach einem Rejoin.
- Die Neon-Wand setzt weiterhin einen unsichtbaren Light-Block über dem angeklickten Block, sofern dort Luft ist.
- Zusätzlich erscheinen vier schmale farbige BlockDisplay-Leuchtkanten auf der Oberseite des angeklickten Blocks. Kein flächiges Overlay.
- Bestehende Neon-Markierungen werden beim Serverstart wiederhergestellt.
- **Wichtig:** Die Kontur ist ein optischer Effekt, kein echtes farbiges Licht. Spieler können die Glowing-Darstellung durch Client-Einstellungen/Shader beeinflussen. Das Plugin wurde hier nicht gegen die Paper-API kompiliert.
- Bei vielen Hunderten Neon-Markierungen entstehen jeweils vier Display-Entities pro Markierung; sparsam einsetzen.

Einrichtung: `/wg team color Huan RED`, `/wg wand neon Huan`, mit Wand Block anklicken. Spieler müssen dem Team zugewiesen sein.

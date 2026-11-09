# Wolfsfell Gameshow – Studio / Neon Update

## Setup
1. `/wg team create Team1` ... Team8, then `/wg team join Team1 <Spieler>`
2. `/wg wand region`: left click POS1, right click POS2.
3. Mark **each full cage interior** with `/wg region save cage:team1` (repeat team2 ... team8).
4. Mark each **central contestant podium/selection zone** with `/wg region save selection:team1` (repeat for each team).
5. `/wg set team:team1` while standing inside the cage; repeat for the other teams.
6. `/wg studio lock` keeps non-admin contestants inside their own cage. **Configure all cage regions first.**
7. `/wg game 1`, then `/wg select on` to allow teams to choose a contestant. Players enter their assigned center region and stand still for 20 ticks.
8. `/wg select off` after selection, then `/wg studio unlock` to release contestants as needed.

## Neon
`/wg wand neon Team1` then right click the desired colored block. The plugin places an invisible Minecraft LIGHT block at light level 15 **one block above** the clicked block, but only if that position is AIR. Clicking again removes the light block. No duplicate full-size BlockDisplays are created. Minecraft light itself is white: choose colored physical blocks for a colored appearance. Do not place the wand on blocks with important space above (doors, interactive structures). Existing saved neon selections will be restored on startup.

## Display
Team members use colored names in TAB, sidebar leaderboard, and Minecraft nametags via scoreboard teams; colored leather chestplates are equipped. Team color is configurable with `/wg team color Team1 BLUE`.

## Known limitations
- The server-side movement restriction is a soft cage boundary, not a physical forcefield. Teleports, commands and other plugins can bypass it.
- Selection requires region marking per team. When no selection region exists for a team, players can leave the cage during selection. Mark all eight before starting.
- The old full-block neon overlays may require server restart to clear transient entities.
- Maven compilation and server runtime were NOT verified in this environment.

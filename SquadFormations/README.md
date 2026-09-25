# SquadFormations

Packet-based fake player squads, disciplined formations, and ambient population
NPCs for Paper 1.21.11 / Java 25.

## Why packets instead of real entities

Every NPC in this plugin (`FakeNPC`) is **not** a real Bukkit/NMS entity. There
is no `LivingEntity`, no goal selector, no pathfinder, and no entity-tracker
overhead on the server. Each NPC is just a `PacketContainer` recipe: a
tab-list entry (`PLAYER_INFO_UPDATE`) + a `SPAWN_ENTITY` packet + periodic
`ENTITY_TELEPORT`/`ENTITY_HEAD_ROTATION` packets. This is what makes it
practical to run 1,000+ of them: cost scales with "packets sent per tick",
not "entities ticked", so there's no AI tick cost, no attribute recalculation,
no pathfinding, and no interference with real mob caps or `/tick` reports.

If you ever do need a *real* interactable entity (e.g. one boss NPC players
can punch), spawn a normal `Player`-shaped mob or use a real entity and call
`entity.setAI(false)`, then drive its position manually the same way
`FollowRunnable` drives fake NPCs — but do **not** do this for hundreds of
units; that reintroduces the tick cost this design avoids.

## Build

```bash
mvn clean package
```

Requires:
- JDK 25 on the build machine (`maven.compiler.release` is pinned to 25).
- Network access to `repo.papermc.io` and `repo.dmulloy2.net` for
  `paper-api` and `ProtocolLib`.

Output jar: `target/SquadFormations-1.0.0.jar`

## Deploy

1. Install [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/)
   on the server (hard dependency, declared in `plugin.yml`).
2. Drop `SquadFormations-1.0.0.jar` into `plugins/`.
3. Restart or `/reload confirm` (restart strongly recommended).
4. Grant `squad.admin` (defaults to `op`).

## Commands

| Command | Effect |
|---|---|
| `/squad spawn <count>` | Spawns `<count>` squad NPCs around you with random 8-char names and, if you've populated the skin pool, random skins. |
| `/squad formation <circle\|square\|grid\|triangle> [radius/spacing]` | Arranges all squad NPCs into the given shape relative to you. |
| `/squad follow <true\|false>` | Toggles live formation tracking — NPCs re-lock to their formation slot as you move/turn. |
| `/squad ambient spawn <count> <radius>` | Spawns `<count>` NPCs that wander randomly within `<radius>` of your current location. |
| `/squad clear` | Despawns everything immediately. |

## Skins

Fake players render with the default Steve/Alex skin until you supply real,
Mojang-signed texture values. Unsigned or fabricated texture/signature pairs
are **not** a shortcut — the client validates the signature and silently
falls back to default skins if it doesn't check out, so there is no
"good enough" placeholder to hardcode here.

To add real skins, pull `(value, signature)` pairs from a service such as
`https://mineskin.org` (their API returns exactly this pair for any skin
image/UUID you feed it) and register them at startup in
`SquadPlugin.onEnable()`:

```java
squadManager.addSkin("<base64 texture value>", "<base64 signature>");
```

`SquadManager` picks a random entry from this pool per spawned NPC. With an
empty pool, NPCs still spawn and behave identically — they just render as
Steve/Alex.

## Known version-fragility notes

Packet field layouts (`PacketType.Play.Server.PLAYER_INFO_UPDATE`,
`SPAWN_ENTITY`, etc.) are ProtocolLib's abstraction over Mojang's wire
protocol, and both ProtocolLib's wrapper surface and the underlying protocol
occasionally shift between game versions. `NPCPacketService` is written
against ProtocolLib 5.3.0's public wrapper API (`PlayerInfoData`,
`WrappedGameProfile`, `getPlayerInfoActions()`, `getDataValueCollectionModifier()`,
etc.) rather than raw NMS reflection, which is the most stable layer
available — but if you upgrade ProtocolLib or the server jar, run a quick
`/squad spawn 1` smoke test and check the console for `FieldAccessException`
or similar before rolling out a mass spawn. If a field index has moved,
ProtocolLib's exceptions name the exact structure modifier at fault, which
makes it a one-line fix.

## Tuning

- `FollowRunnable.LERP_FACTOR` — how snappily squad NPCs catch up to their
  formation slot each cycle (0–1, higher = snappier).
- `FollowRunnable.PERIOD_TICKS` / `AmbientRunnable.PERIOD_TICKS` — how often
  position packets go out. Raising these reduces packet volume at the cost
  of visibly choppier motion; useful if you're pushing thousands of NPCs on
  a bandwidth-constrained server.
- `SquadCommand.MAX_SPAWN_PER_COMMAND` — hard ceiling per `/squad spawn` or
  `/squad ambient spawn` call, to stop a typo from requesting 500,000 NPCs.

## Persistence

NPCs are purely in-memory (`SquadManager`'s maps) and do not survive a
server restart or `/squad clear`; ambient NPCs are anchored by world
coordinates, not by chunk, so they never "despawn" on chunk unload the way
a real entity would — there is no entity for the chunk system to unload in
the first place.

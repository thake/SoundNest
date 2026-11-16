# Driving the app over adb (`tools/soundnest-cli`) — Debug Builds Only

Debug builds (`cliEnabled = true`) answer playback and AutoMix test commands sent over adb, so automated tests can inspect state without screenshots or taps. Non-debug builds disable `CliReceiver` in the manifest (`cliEnabled = false`) and refuse CLI broadcasts at runtime.

```bash
tools/soundnest-cli [-p io.github.thake.soundnest.dev] [-s serial] [-t 40] [--raw] <command> [args…]
```

Each command is one broadcast (`<package>.CLI`, guarded by `android.permission.DUMP`).

## Commands

| Command | Does |
| --- | --- |
| `status` | Version, debug flag, signed in or not, what plays (`uri`, `position`, `duration`), whether the screen is open, and `player` (`local` vs `account`). |
| `play <spotify:…> [context]`, `pause`, `resume`, `next`, `previous` | Player playback commands. |
| `nav <route>` | Navigates the open screen to a route (`settings`, `queue`…). |
| `automix [on\|off]` | Turns AutoMix on or off and prints where it stands. |
| `automix.segue [on\|off]` | Turns plan-based transitions on or off (`docs/automix/segue-design.md`). |
| `automix.status` | AutoMix's state (deck, plan, position) and recent journal lines. |
| `automix.settings [<name> <value>]` | Reads or updates AutoMix settings as JSON. |
| `automix.next` | Requests Next transition on the active local player. |
| `automix.files <piece> <piece>…` | Plays local device audio files matching each piece. |
| `automix.seek <ms>` | Moves in the local player's song. |

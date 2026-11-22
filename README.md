# TV Cleanse

Turn off the bloat on a slow Android TV or Fire TV over Wi-Fi. Every change is logged and reversible. Nothing is uninstalled.

The idea of using adb to switch off a TV's junk apps is not new, and this project was inspired by [Ansub/tv-detox](https://github.com/Ansub/tv-detox). No code was copied from it. Everything here was written from scratch.

## Status

The script has passed its tests against a simulated TV (`bash tests/run.sh`). It has **not** yet been run on a real TV. Treat the first run as a test, on a TV you can afford to reset.

## Risks

- Turning off the wrong system package can leave a TV stuck at the logo, in a restart loop, or without Wi-Fi. Factory reset fixes it, and you lose your settings.
- The script refuses core Android and Google packages, home screens and setup apps, anything Android marks PERSISTENT, and anything with a name suggesting Wi-Fi, Bluetooth, remote, keyboard, settings or updates. That list errs on the side of refusing, but it can't know every vendor's packages. You are the last check.
- Leaving adb debugging on lets other devices on your network control the TV. Run `finish` when you're done.

## What you need

- A computer on the same Wi-Fi as the TV, with [adb](https://developer.android.com/tools/releases/platform-tools) and bash (Git Bash works on Windows).
- On Android TV: Settings → Device Preferences → About → press OK on **Build** 7 times, then in Developer options turn on **USB debugging** (and Network debugging if shown).
- On Fire TV (menus vary): Settings → My Fire TV (or Device & Software) → About, select the device name 7 times, then in Developer options turn on **ADB debugging**.
- The TV's IP address, from the TV's network or About screen.

## Use it

```
./tvcleanse.sh <TV_IP> info          # make, model, Android version
./tvcleanse.sh <TV_IP> mem           # free memory
./tvcleanse.sh <TV_IP> scan          # every package, with a safety guard column
./tvcleanse.sh <TV_IP> disable pkg1 pkg2
./tvcleanse.sh <TV_IP> anim off      # animations off
./tvcleanse.sh <TV_IP> undo          # reverse everything, newest first
./tvcleanse.sh <TV_IP> finish        # debugging off, disconnect
```

The first time, the TV asks "Allow debugging?". Press **Allow**.

The log lives in `~/.tvcleanse/`. If an undo step fails, it stays in the log so you can run `undo` again.

## Let Claude Code drive it

Paste this into [Claude Code](https://claude.com/claude-code) on your computer, with your details filled in:

```text
Help me clean up my TV and make it faster. It is a <brand and model>, and its IP address is <IP>.

Download https://github.com/gblanchard77-beep/tv-cleanse and use its tvcleanse.sh. Keep every instruction to me very simple, one step at a time.

1. Run info and mem. Tell me whether it is an Android TV or a Fire TV.
2. Run scan. For every package that is not BLOCKED, look up what it is online and tell me in plain English: safe to turn off, needed, or an app I might use. Ask me yes or no for the ones I might use.
3. Ask me before every change. Only turn things off with the disable command. Never uninstall anything, and never try to get around a REFUSE.
4. After every few changes, confirm the TV still answers and is on Wi-Fi.
5. Run anim off.
6. Restart the TV, run mem again, and show me the free memory before and after.
7. At the end, tell me how to undo everything, run finish, and remind me to check that debugging is off.
```

## Tests

`bash tests/run.sh` runs the script against a fake adb in `tests/mock-adb`. It checks the guards, the log, undo and the unreachable-TV case. It can't tell you how a real TV behaves.

## License

MIT. See [LICENSE](LICENSE).

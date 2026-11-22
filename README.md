# TV Cleanse

Turn off the bloat on a slow Android TV or Fire TV over Wi-Fi, and swap in a simple home screen: a clock, your apps, your HDMI inputs, no ads. Every change is logged and reversible. Nothing is uninstalled.

The idea of using adb to switch off a TV's junk apps is not new, and this project was inspired by [Ansub/tv-detox](https://github.com/Ansub/tv-detox). No code was copied from it. Everything here was written from scratch.

## Status

The script has passed its tests against a simulated TV (`bash tests/run.sh`). The home screen (`home-install`, the Google TV Home button helper, Edit apps and moving apps) has been tested on one real TV: a TCL Google TV on Android 11. The cleanup commands (`scan`, `disable`, `anim`, `undo`) have not been tried on a real TV yet. Treat the first run as a test, on a TV you can afford to reset.

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
./tvcleanse.sh <TV_IP> home-install  # install the TV Cleanse home screen
./tvcleanse.sh <TV_IP> home-remove   # remove it
./tvcleanse.sh <TV_IP> undo          # reverse everything, newest first
./tvcleanse.sh <TV_IP> finish        # debugging off, disconnect
```

The first time, the TV asks "Allow debugging?". Press **Allow**.

The log lives in `~/.tvcleanse/`. If an undo step fails, it stays in the log so you can run `undo` again.

## The home screen

A small Java app (`launcher/`, no libraries): a clock with AM/PM, a grid of your apps using each app's own TV banner, your HDMI inputs, and a Settings button. It reads the app list from the TV, so apps you turn off disappear from it.

- **Choose your apps:** the **+ Edit apps** tile at the end of the grid lists every app. Press OK to show or hide one. New apps show up automatically.
- **Move apps:** hold OK on an app until it's outlined in blue, move it with the arrows, then press OK. The order is saved on the TV.
- `home-install` downloads the APK from this repo's releases, **checks its SHA-256 checksum**, installs it and makes it the default home screen. It does **not** disable the TV's own home screen. That's the step that left other TVs stuck.
- `home-remove` uninstalls it, and the TV's own home screen comes back. `undo` does this too.

### Google TV

Google TV's own home screen always wins the Home button, and switching it off can leave the TV stuck restarting (it can also hand Home to the setup app, which can't be switched off safely). So on Google TV, `home-install` turns on an optional accessibility helper, **TV Cleanse Home button**, instead. The idea comes from [Ansub/tv-detox](https://github.com/Ansub/tv-detox); the code is our own.

- When Google TV's home screen opens, the helper opens TV Cleanse over it, about 0.2 seconds later. You may see a quick flicker of Google TV.
- It only receives "window changed" events from Google TV's home app. It can't read the screen or see key presses.
- **Hold Back** on TV Cleanse to go to Google TV's home. It stays there until the TV restarts or you open TV Cleanse Home from the apps row.
- If TV Cleanse fails to open 3 times in a row, the helper stops trying until it next opens.
- Turn it off any time in **Settings > Accessibility > TV Cleanse Home button**. `home-remove` turns it off too.
- If Google TV's home is already on screen, pressing Home again does nothing, because nothing "opens". Open TV Cleanse from the apps row once, and Home works from then on.

### Building

- GitHub builds the APK in `.github/workflows/build.yml` and attaches it to each release (a tag starting with `v`). To build it yourself: install JDK 17 and the Android SDK, set `ANDROID_HOME`, run `bash launcher/build.sh`, then `TVCLEANSE_APK=launcher/out/tvcleanse-home.apk ./tvcleanse.sh <TV_IP> home-install`.
- Each build is signed with a fresh key unless you set `KEYSTORE` (and `KEYSTORE_PASS`). A build with a different key replaces the old one by uninstalling it first, which resets your app choices and switches the Google TV helper off (`home-install` turns it back on). Keep one keystore to update in place.
- On Fire TV, Amazon has blocked custom home screens with updates. Expect `home-install` to install but not take over the Home button.

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

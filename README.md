# atv-letterwise

A replacement keyboard for Android TV that types by **prefix disambiguation** instead of
by driving a cursor around a grid of letters.

Grid keyboards — Gboard TV, LeanKeyboard, the in-app keyboards in Netflix and YouTube —
all cost roughly **10 keystrokes per character**, because every letter means travelling
the cursor there and confirming. This types one press per letter and lets a character
trigram model work out which letter you meant.

**Status: early. Not usable yet.** The disambiguation core and its simulator work and are
tested; the IME itself is not built.

---

## Hardware requirement

**A remote with number keys `0`–`9`.** Most TV-integrated remotes have them, for
broadcast channel entry. Chromecast-with-Google-TV and Shield-class streaming remotes do
**not**, and this keyboard will not work on them.

That is a deliberate trade. LetterWise's published KSPC of 1.15 was measured on eight
ambiguous keys, and building it on four d-pad directions instead would throw that figure
away and produce a different, unmeasured method. If you have a d-pad-only remote, the
companion project [`atv-h4`](https://github.com/vagrant326/atv-h4) targets exactly that
case — it is designed for four keys from the start.

## How it types

| Input | Action |
|---|---|
| `2`–`9` | Select letter group, phone-keypad layout |
| `0` | Space |
| `1` | Punctuation; long press toggles PL / EN |
| `DPAD_UP` / `DOWN` | Walk the candidate letters for the current position |
| `DPAD_RIGHT` | Accept the current character |
| `DPAD_LEFT` | Backspace |
| `DPAD_CENTER` | Enter — finish input |
| `BACK` | Dismiss; long press clears |

You do not have to press accept. Pressing the next letter's group key accepts the
previous character automatically, exactly as on a phone keypad — accept exists for the
end of a word and for deliberately freezing a character you can see is already right.

Both Polish and English are supported, as two trigram tables rather than two
dictionaries. Anything is typable: proper nouns, film titles, invented words, passwords.
A word the model has never seen costs extra presses, never a dead end.

## Updating

Settings → Check for updates. It compares the installed version against the latest
release, downloads the APK and hands it to the system installer. The first time, Android
will ask you to allow this app to install packages; the screen links straight there.

Nothing checks on its own — no background job, no boot receiver, no poll on keyboard start.
It happens when you press the button and not otherwise.

## The `INTERNET` and `REQUEST_INSTALL_PACKAGES` permissions, and why a keyboard has them

**A keyboard that can reach the network and install packages is a legitimate thing to be
suspicious of.** An IME sees every password and every card number typed on the device. This
one holds both permissions because it is distributed by sideloading rather than through a
store, so it has no other way to update itself.

What they are allowed to do here:

- **The IME process never opens a socket and never installs anything.** All of it lives in
  a separate process (`:updater`) — the update activity and the `FileProvider` that hands
  the APK over. The component handling your keystrokes contains none of that code.
- **Nothing runs unless you press a button.** No background job, no boot receiver, no
  periodic poll, no check when the keyboard starts.
- **Two requests, no payload.** One `GET` for the latest release tag, one for the APK. No
  device identifier, no version histogram, no analytics, no crash reporting.
- **Nothing you type ever leaves the device.** There is no telemetry path in this codebase
  and there will not be one.
- **The APK is fetched from this repository's releases and installed through the system
  installer**, which shows you what is being installed and by whom. Nothing is installed
  silently, and it cannot be.

Both come out if the project ever gets a store listing. Until then the code is small enough
to check by reading it, which is the point.

## Building

Everything runs in the dev container — no JDK, Android SDK or Gradle cache on the host:

```bash
docker compose -f docker/compose.yaml run --rm -w /work/atv-letterwise dev ./gradlew test
```

The `core` module is plain Kotlin with no Android dependencies, on purpose: the simulator
and the shipped IME call the same disambiguation code, so a KSPC measured on a laptop is
the KSPC that ships.

## Branching and the two channels

| Branch | What runs | Result |
|---|---|---|
| `feature/**`, `fix/**`, pull requests | CI — tests, lint, both debug APKs | artifacts only |
| `develop` | Release dev | `dev-x.y.z`, installs as **atv-letterwise dev** |
| `main` | Release | `vx.y.z`, installs as **atv-letterwise** |

**The dev build is a separate application**, not just a separate file: it carries its own
`applicationId`, so it installs alongside the released one and both appear in the keyboard
picker. That is the point — an experiment that misbehaves does not take the working keyboard
with it, and this project has already lost a TV's navigation to one.

Each channel counts its own versions and updates only from its own releases, matched by tag
prefix. A dev build will never offer to install a production APK over itself.

Day to day: work on `develop`, which publishes a dev build on every push. To ship, open a
pull request from `develop` to `main` and merge it. **Do not delete `develop`** — it is
long-lived. After merging, bring it back in line so the next dev release contains the merge:

```bash
git switch develop && git merge --ff-only main && git push
```

## Installing

In the AFTVnews Downloader app, enter code **8662742**. Seven digits on the remote beats
entering a URL with a grid keyboard, which is the problem this project exists to solve.

Or use either address directly. Both are permanent and both always serve the newest build:

```
https://github.com/vagrant326/atv-letterwise/releases/download/latest/atv-letterwise.apk
https://github.com/vagrant326/atv-letterwise/releases/latest/download/atv-letterwise.apk
```

The first is a rolling `latest` release that each build recreates. The second is resolved
by GitHub itself from the newest versioned release — no extra machinery, but the path shape
is easy to confuse with the first.

Either way the asset name deliberately carries no version number, which is what keeps the
URL stable. The version lives in the release tag and in the app's own settings screen.
[All releases](https://github.com/vagrant326/atv-letterwise/releases) are listed if you
need a specific older one.

Then Settings → System → Keyboard, select it, and enable it. Android requires that step
manually for every IME.

**If the keyboard ever leaves the TV unnavigable**, press `HOME` — an IME cannot intercept
it — and switch keyboards or uninstall from there. A USB mouse also always works, because
pointer events never reach the keyboard's key handling.

## Licence

MIT. See [LICENSE](LICENSE).

Trigram tables are built from OpenSubtitles (OPUS) and Wikidata; the corpus itself is
fetched by script rather than vendored, and attribution lives with the fetch tooling.

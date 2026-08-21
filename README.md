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

## The `INTERNET` permission, and why a keyboard has one

**A keyboard with network access is a legitimate thing to be suspicious of.** An IME sees
every password and every card number typed on the device. This one has `INTERNET` because
it is distributed by sideloading rather than through the Play Store, so it has no other
way to tell you an update exists.

What that permission is allowed to do here:

- **The IME process never opens a socket.** The update check lives in the settings
  activity, in a separate process (`:updater`). The component that handles your
  keystrokes contains no networking code at all.
- **It runs only when you press "check for updates".** No background job, no boot
  receiver, no periodic poll, no check when the keyboard starts.
- **One request, no payload.** A `GET` to this repository's GitHub releases endpoint.
  No device identifier, no version histogram, no analytics, no crash reporting.
- **Nothing you type ever leaves the device.** There is no telemetry path in this
  codebase and there will not be one.

This comes out if the project ever gets a Play Store listing. Until then the code is
small enough to check by reading it, which is the point.

## Building

Everything runs in the dev container — no JDK, Android SDK or Gradle cache on the host:

```bash
docker compose -f docker/compose.yaml run --rm -w /work/atv-letterwise dev ./gradlew test
```

The `core` module is plain Kotlin with no Android dependencies, on purpose: the simulator
and the shipped IME call the same disambiguation code, so a KSPC measured on a laptop is
the KSPC that ships.

## Branching

| Branch | What runs |
|---|---|
| `develop`, `feature/**`, `fix/**`, pull requests | CI — tests, lint, debug APK artifact |
| `main` | Release — raises the version, signs, publishes to Releases |

**A push to `main` is a release.** There is no separate tagging step: the next version is
computed from the highest existing tag and raised by a patch. For a minor or major bump,
run the Release workflow manually from the Actions tab and choose the level.

## Installing

Grab the APK from [Releases](https://github.com/vagrant326/atv-letterwise/releases) and
open the URL in a downloader app on the TV. Then Settings → System → Keyboard, select it,
and enable it. Android requires that step manually for every IME.

## Licence

MIT. See [LICENSE](LICENSE).

Trigram tables are built from OpenSubtitles (OPUS) and Wikidata; the corpus itself is
fetched by script rather than vendored, and attribution lives with the fetch tooling.

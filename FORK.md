# Namco System 246 EMU — how this fork is put together

This is ARMSX2 with the pcsx2x6 arcade layer transplanted in and an arcade-only launcher on top.
Both upstreams keep moving, so the fork is arranged around one goal: **make an update cost only
as much as it has to.**

Run this before starting an update — it answers the only expensive question:

```bash
tools/upstream.sh            # against ARMSX2
tools/upstream.sh pcsx2x6    # against PS2Homebrew-arcade/pcsx2x6
```

**ARMSX2** is this tree's actual ancestor, so the report prints the *intersection*: the files we
modified that upstream also changed. Everything outside that set merges without anyone reading
it. At the time of writing that is **8 files**, out of 120 we touch and 30 upstream moved.

**pcsx2x6 is not an ancestor.** It and ARMSX2 are separate forks of PCSX2, so asking git to diff
our fork point against pcsx2x6's tip compares two unrelated histories and answers "10,408 files
changed" — true, and useless. What we take from pcsx2x6 is the arcade delta, so that mode asks a
different question: what changed *in the arcade code* since the commit recorded in
`tools/pcsx2x6-base.txt`. Update that file whenever you pull their work across.

---

## The shape

| | files | conflicts with upstream? |
|---|---|---|
| Files we added | 107 | never |
| Upstream files we modified | 120 | only if upstream touches the same one |

The first row is where nearly all of this fork's behaviour lives. That is deliberate and it is
the rule to keep following: **when something can go in a new file, put it in a new file.**

The clearest illustration is what this rule cost when it was broken. The branding was originally
done by find-and-replace across `I18n.kt` and all nineteen translation JSONs — 20 files and
21,936 changed lines, about three quarters of the whole fork, almost all of it reformatting
noise. Moving it to a lookup-time hook (`i18n/BrandStrings.kt`) put those files back to
byte-identical with upstream and left 12 lines behind. Same behaviour, 1/1800th of the merge
cost.

---

## Where we hook into upstream files, and why

Everything below is a place we could not avoid touching. Each entry says what to look for if git
reports a conflict there.

### Emulator core (from pcsx2x6)

These carry the arcade transplant. They are mostly *additive* — new branches guarded by an
arcade check — so upstream changes around them usually apply cleanly.

| file | what we put there |
|---|---|
| `pcsx2/VMManager.cpp` | The `.acgame` boot path: manifest parse, dongle staging into memcards/, SRAM path resolution and seeding, ACATA env, JVS mode. Plus `IsArcadeGame()` and an SRAM flush in `SetPaused`. |
| `pcsx2/DEV9/AC*.{cpp,h}` | **New files.** The arcade hardware: ACATA/ACATAPI (ATAPI DVD over CHD), ACJV (JVS I/O), ACCORE, ACRAM, ACSRAM, ACUART. |
| `pcsx2/IopBios.cpp` | `host:` root resolution. We added the normalised-override compare — Android names one file through several mount aliases, and the raw string compare missed. |
| `pcsx2/IopMem.cpp`, `IopModuleNames.cpp`, `Input/InputManager.cpp`, `USB/usb-lightgun/guncon2.cpp`, `DEV9/DEV9.cpp`, `GameList.cpp` | Arcade hooks from pcsx2x6. |
| `pcsx2/Config.h`, `Pcsx2Config.cpp` | Arcade config fields. |
| `bin/resources/GameIndex.yaml` | The System 246/256 gameid entries. Purely appended; a conflict here is almost always both sides adding entries, and both should be kept. |
| `pcsx2/arm64/iR5900-arm64.cpp` | The ARM64 EE recompiler taught the 128 MB map the arcade boot needs. **Ours, not pcsx2x6's** — upstream's ARM64 rec was MainRam-only. |
| `pcsx2/ImGui/ImGuiManager.cpp` | A floor under `WindowMinSize` after `ScaleAllSizes`, and an MTGS thread guard on the bezel overlay. |

### Android launcher

| file | what we put there |
|---|---|
| `platforms/android/.../i18n/I18n.kt` | **One hook**, in `get()`, calling `BrandStrings.resolve`. Nothing else. The string tables themselves are untouched. |
| `platforms/android/.../runtime/MainActivityRuntime.kt` | `resolveDocumentUriToPosix` and its use in `launchGame` (an arcade `.acgame` needs a real path, not a SAF URI), landscape locking in `applyEmulationOrientation`, and one call into `ArcadeBios.decide` where the effective BIOS is resolved. |
| `platforms/android/.../data/library/GameLibraryRepository.kt` | `.acgame` parsing, arcade payload-dir collapse, and the arcade-only extension set. |
| `platforms/android/app/src/main/cpp/native-lib.cpp` | The JVS bridge: pad → JVS, the JNI entry points the arcade panel calls, and the lightgun bridge (an action → the running game's JVS trigger/pedal/start bits, resolved from `ACJV::GetGunMapping`). |
| `platforms/android/.../navigation/AppNavigation.kt`, `AppRoute.kt` | Routes to the premium screens; removed Network/Skins categories. |
| `platforms/android/.../ui/premium/*` | **New files.** The whole launcher: home, library, art picker, add-game sheet, icon set, silver trace, branding. |
| `platforms/android/.../ui/settings/*`, `ui/emulation/*`, `ui/common/ArmsComponents.kt` | Restyled to the premium system, icons instead of glyphs, and removals (Network, Skins, Achievements, Friends, disc swap). **This is the fork's largest UI delta and the most likely place to conflict.** |
| `AndroidManifest.xml` | `screenOrientation="sensorLandscape"` on both activities, theme rename. |
| `res/values/strings.xml`, `themes.xml`, `mipmap-anydpi-v26/*`, `drawable/ic_launcher_*` | App name and icon. |

---

## Rules that keep the cost down

1. **New file first.** If a change can live in a file upstream does not have, it goes there. A
   hook in an upstream file should be one call, not a block of logic.
2. **Never reformat a file you are editing.** The `BrandStrings` story above is what that costs.
   Match the surrounding style and change only the lines you mean to.
3. **Prefer runtime resolution over source edits** for anything that is "the same data, said
   differently" — names, labels, defaults.
4. **Keep the arcade core delta separate from the UI delta.** They come from different upstreams
   and update on different schedules.
5. **Additive beats invasive** in data files (`GameIndex.yaml`, the compatibility list): appended
   entries conflict trivially and are resolved by keeping both sides.

## Updating

```bash
tools/upstream.sh                       # see the real cost first
git fetch armsx2-upstream main
git merge armsx2-upstream/main          # or rebase, if you prefer a linear history
```

Resolve only the files the report listed. For each, this document says what we put there; keep
our hook and take upstream's surrounding changes. Then:

```bash
cd platforms/android && ./gradlew :app:testGithubDebugUnitTest
tools/i18n-missing.py                   # strings that would fall back to English
```

Those tests exist because the boot chain (dongle staged -> ACATA opens the CHD -> `proverb.elf`
loads -> SRAM found) is what a bad merge breaks first, and it breaks *silently*: the app still
builds, still installs, still opens. They cover the half of that chain which is ordinary Kotlin
and needs no device -- the `.acgame` the wizard writes, the compatibility list (the parse and the
asset we actually ship), and the BIOS rules -- so a merge that reverts one of them fails the build
instead of waiting for someone to notice a black screen. `.github/workflows/arcade-checks.yml`
runs the same command on every push that touches `platforms/android/`; it is a new file, not a
step bolted onto upstream's `build-all.yml`, and it does not rebuild the APK because upstream's
pipeline already does.

`tools/i18n-missing.py` covers the other silent one: upstream adds English strings, the
translation tables catch up later or never, and a key with no entry falls back to English at
lookup time -- per string, so the result is a Portuguese screen with an English paragraph in the
middle of it. Nothing fails; it just reads badly, on whichever screen nobody has opened yet.

The other half is native and still needs a device: build the APK and boot one arcade game end to
end.

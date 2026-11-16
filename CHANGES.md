# Changes in 1.1.0

## Bugs fixed

| Area | Bug | Fix |
|---|---|---|
| Crash | `java.time` used everywhere, but minSdk is 24: the app crashed on Android 7.x | Core-library desugaring enabled |
| Shift timer | "End & log" cleared the timer *before* the editor opened. Closing the editor without saving lost the tracked time | Timer is paused, and only cleared after the shift is saved |
| Shift timer | A shift that ran past midnight was logged on the wrong day | Uses the date the timer started |
| Numbers | Settings removed commas, so `3,3` % tax became **33 %** and `1,5`× overtime became 15× | New locale-safe `NumberInput.parse` used by every number field |
| Shift editor | The overtime threshold was derived from the typed "regular hours", so the daily-wage overtime rate changed per shift and a long break could turn a whole shift into overtime | One "hours worked" field. Overtime comes from the "overtime starts after" setting |
| Shift editor | The overtime multiplier wasn't validated; bad input was silently replaced | All fields validated, with messages |
| Shift editor | Rotating the phone lost everything typed | All editor state is saved across rotation |
| App lock | On Android 9 and lower, phones with only a PIN/pattern (no fingerprint) could be locked out | The check and the prompt use the same authenticators |
| App lock / sign-in | Activity looked up by casting `LocalContext`, which can fail silently | `LocalActivity` |
| Sync | Settings were not pushed to a second account after switching accounts | Per-account reset |
| Sync | Each settings change from another device was echoed back to the server | Echo suppressed |
| Sync | A cloud shift with a malformed date was silently moved to "today" | Skipped |
| Backup | One bad entry made the whole backup file "invalid" | Bad entries are skipped |
| Backup | The auto-backup file could be corrupted by a crash mid-write | Atomic write |
| Network | Reported "online" on captive-portal Wi-Fi and flipped offline while another network was still up | Tracks validated networks |
| Pay settings | Back button discarded unsaved changes without asking | Confirmation |
| Build | `kotlin.compiler.execution.strategy=in-process` breaks the Compose compiler under AGP 9 | `daemon` |
| Build | Release had no minification; unused Gemini/Retrofit/Moshi/OkHttp/App Check libraries shipped | R8 + resource shrinking, unused dependencies removed (release APK 3.6 MB) |
| Privacy | Android backup copied everything, including sign-in tokens | Only shifts and settings are backed up |

## Simpler UI

- Tabs: **Home · Shifts · Reports · Settings**.
- **Home**: one summary card (earned, hours, goal, paid/unpaid, "I got paid for this month"), the shift timer, and recent shifts. The 4-tile stats grid, tax editor and workplace filter are gone.
- **Log a shift**: date chips, workplace chips, one *Hours worked* field with big −/+ buttons, break chips, "I've been paid" switch, and a note. Rate and overtime are folded into **Pay details**, pre-filled from settings. The pay estimate updates live.
- **Shift rows**: tap to edit. The tiny 3-dot menu is gone; mark-paid and delete live in the editor.
- **Reports**: week total, bar chart, breakdown, a per-workplace split, and *Share pay statement*.
- Removed the unused "pay period" setting.

## Accessibility

- Colours re-tuned to meet WCAG AA contrast (secondary text, green/amber status text, field outlines).
- Status is never shown by colour alone: Paid/Unpaid badges have an icon and a word.
- Minimum text size is 12sp, body text is 15–16sp, and everything scales with the system font size. Buttons use minimum heights, so large text is never clipped.
- Touch targets are at least 48dp, and primary buttons are 52dp.
- TalkBack:
  - Section headings are marked as headings.
  - Each shift reads as one sentence, with "Mark as paid" and "Delete shift" as actions.
  - The chart and the timer have spoken descriptions.
  - Errors are announced (live regions).
  - Whole setting rows act as one switch.
  - Navigation icons aren't read twice.
- No tab cross-fade animation.

## Still to verify on a real device

- Google sign-in on the release build (needs the release SHA-1 in Firebase).
- Biometric unlock on Android 7–9.
- Firestore rules deployment.
- TalkBack walkthrough.

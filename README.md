# Ferro

A training log for the Galaxy Z Fold 8 Ultra, built on Cerniera's [Kit](https://github.com/cerniera/kit)
to the Bellezza Necessaria mandate.

**Folded**, Ferro is a one-handed logger: the exercise in hand, today's target, weight and reps on
steppers big enough for a thumb, Log set, then the rest bar. **Unfolded**, the coach opens beside it:
the exercise's curve (best estimated 1RM per session), what to lift next time and why, and what the
numbers are saying.

## What the coach watches

- **Next target:** double progression. Every set at the top of the rep range adds the exercise's increment;
  missing the bottom two sessions running steps back.
- **Plateaus:** no gain in best estimated 1RM over three sessions.
- **Drop-offs:** reps on the last set under 70% of the first, or the weight coming down after set 1.
- **Fatigue:** a session's volume 15% under the last three of that day, or reps fading on three or more exercises.
- **Recovery:** the same day trained again inside 48 hours, or a gap of more than nine days.
- **Balance:** legs getting fewer sets than push or pull over a fortnight (legs are the priority this bulk).
- **Bodyweight:** the four-week trend against the common 0.25–0.5% a week guideline for a lean bulk.

All thresholds live in `Coach.kt`. They are general rules of thumb, not medical or coaching advice.

## Data

Everything stays on the phone in one JSON file. **Back up** and **Restore** on the home screen save
and load it as a file you choose.

## Building

`git clone --recurse-submodules`, then GitHub Actions builds the APK on every push and runs it on an
emulator. Each build on `main` is published as a release (`build-N`) with `Ferro.apk`.

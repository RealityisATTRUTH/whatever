"""
Turns a recorded player's fights into a Fight Bot style profile.

Usage:
  python calibrate.py PLAYER FOLDER_OR_FILES... [--kit sword] [--out profiles/PLAYER.properties]

FOLDER is usually the recorder's fights folder. Every .training.jsonl file under it is read.
Only lines where PLAYER is the fighter are used. With --kit, only that kit's fights count.
"""
import argparse
import glob
import json
import os
import statistics


def pct(values, q):
    if not values:
        return None
    s = sorted(values)
    i = min(len(s) - 1, max(0, int(round(q * (len(s) - 1)))))
    return s[i]


def clip(v, lo, hi, default):
    if v is None:
        return default
    return max(lo, min(hi, v))


def load(paths, player, kit):
    files = []
    for p in paths:
        if os.path.isdir(p):
            files += glob.glob(os.path.join(p, "**", "*.training.jsonl"), recursive=True)
        else:
            files.append(p)
    rows = []
    for f in files:
        with open(f, encoding="utf-8") as fh:
            for line in fh:
                try:
                    r = json.loads(line)
                except ValueError:
                    continue
                if r.get("player", "").lower() != player.lower():
                    continue
                if kit and r.get("kit") != kit:
                    continue
                rows.append(r)
    # Keep each fight's records in order.
    rows.sort(key=lambda r: (r.get("fight", 0), r.get("episode", ""), r.get("tick", 0)))
    return rows


def calibrate(rows, name):
    attacks = [r for r in rows if r["action"]["attack"]]
    landed = [r for r in attacks if r["result"]["hitWithin10Ticks"]]
    p = {"name": name}

    p["preferredDistance"] = round(clip(statistics.median([r["state"]["distance"] for r in landed]) if landed else None, 2.0, 3.0, 2.7), 2)
    p["tooClose"] = round(clip(pct([r["state"]["distance"] for r in rows], 0.15), 1.2, 2.2, 1.8), 2)
    charges = [r["state"]["attackCharge"] for r in attacks if r["state"]["attackCharge"] is not None and r["state"]["attackCharge"] >= 0]
    p["chargeThreshold"] = round(clip(pct(charges, 0.25), 0.5, 0.98, 0.92), 2)
    aims = [r["state"]["facingErrorDeg"] for r in attacks if r["state"]["facingErrorDeg"] is not None]
    p["maxAimError"] = round(clip(pct(aims, 0.8), 10, 40, 25), 1)

    # What they did in the next few records after an attack.
    wtap = stap = after = close_after = 0
    for i, r in enumerate(rows):
        if not r["action"]["attack"]:
            continue
        nxt = rows[i + 1:i + 3]
        if not nxt:
            continue
        # A W-tap is a sprint hit, then sprint drops and comes back within a few records.
        if r["state"]["sprinting"]:
            after += 1
            later = rows[i + 1:i + 5]
            dropped = [k for k, n in enumerate(later) if not n["state"]["sprinting"]]
            if dropped and any(n["state"]["sprinting"] for n in later[dropped[0] + 1:]):
                wtap += 1
        if r["state"]["distance"] < 2.2:
            close_after += 1
            if any(n["action"]["move"] == "retreat" for n in nxt):
                stap += 1
    p["wTapChance"] = round(wtap / after, 2) if after else 0.8
    p["sTapChance"] = round(stap / close_after, 2) if close_after else 0.6

    # How long they strafe one way before switching, in ticks. Records are 2 ticks apart.
    runs, cur, last = [], 0, None
    for r in rows:
        m = r["action"]["move"]
        if m in ("strafe_left", "strafe_right"):
            if m == last:
                cur += 1
            else:
                if cur:
                    runs.append(cur)
                cur = 1
            last = m
    if cur:
        runs.append(cur)
    if runs:
        p["strafeSwitchMin"] = int(clip(pct(runs, 0.25) * 2, 4, 40, 8))
        p["strafeSwitchMax"] = int(clip(pct(runs, 0.75) * 2, p["strafeSwitchMin"] + 2, 60, 24))

    # Jumps right after losing health.
    hurt_jumps = hurt = 0
    for prev, r in zip(rows, rows[1:]):
        if r["state"]["ownHp"] < prev["state"]["ownHp"] - 0.01:
            hurt += 1
            if r["action"]["jump"]:
                hurt_jumps += 1
    p["jumpResetChance"] = round(hurt_jumps / hurt, 2) if hurt else 0.5

    air = [r for r in attacks if r["state"]["airborne"] and not r["state"]["sprinting"]]
    p["critJumpChance"] = 0.15 if attacks and len(air) / len(attacks) > 0.5 else 0.0

    eats = [r["state"]["ownHp"] for r in rows if (r["action"].get("use") or "").startswith("eat")]
    p["healAt"] = round(clip(statistics.median(eats) if eats else None, 4, 16, 9), 1)

    summary = {
        "records": len(rows),
        "attacks": len(attacks),
        "landed within half a second": len(landed),
        "eats": len(eats),
    }
    return p, summary


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("player")
    ap.add_argument("paths", nargs="+")
    ap.add_argument("--kit")
    ap.add_argument("--out")
    a = ap.parse_args()
    rows = load(a.paths, a.player, a.kit)
    if not rows:
        print("No records for", a.player, "in those files" + (" for kit " + a.kit if a.kit else "") + ".")
        return
    name = a.player.lower() + "_style"
    profile, summary = calibrate(rows, name)
    out = a.out or os.path.join("profiles", name + ".properties")
    os.makedirs(os.path.dirname(out) or ".", exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        f.write("# Fight bot profile calibrated from " + a.player + "'s recorded fights\n")
        for k, v in profile.items():
            f.write(f"{k}={v}\n")
    print("Read", summary)
    for k, v in profile.items():
        print(f"  {k} = {v}")
    print("Saved", out)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Write an invented ChilliBook v1 backup for emulator verification."""
import argparse
from datetime import date, timedelta
import json
from pathlib import Path
from uuid import NAMESPACE_DNS, uuid5


def generate(end, length):
    start = end - timedelta(days=length - 1)
    days, checks, activities, episodes = [], [], [], []

    def stamp(day):
        return {"at": f"{day}T18:15:00Z", "zone": "Europe/London"}

    def sample_id(name):
        return str(uuid5(NAMESPACE_DNS, f"chillibook.example/{name}"))

    for offset in range(length):
        day = start + timedelta(days=offset)
        age = (end - day).days
        if age == 1 or age % 37 == 15:
            continue
        work = day.weekday() < 5
        social = age % 6 == 2
        chores = age % 5 == 3
        day_rec = dict(date=str(day), work=work, social=social, household=chores,
                       wellbeing=[3, 2, 4, 2, 3, 1, 2][age % 7], impact=None,
                       context=["Fighting a bug"] if age == 13 else [], workOff=None, **stamp(day))
        days.append(day_rec)
        for slot in ("am", "pm"):
            if (slot == "am" and age % 23 == 8) or (slot == "pm" and age % 19 == 5):
                continue
            energy = ([2, 1, 1, 3, 2, 1, 2, 2, 1, 3, 2] if slot == "am"
                      else [1, 2, 2, 1, 2, 1, 2, 0, 2, 3, 1])[age % 11]
            checks.append(dict(date=str(day), slot=slot, energy=energy,
                               sleep=(2 if age == 0 else age % 4) if slot == "am" else None,
                               symptoms={"Brain fog": age % 4} if age % 3 != 0 else {},
                               **stamp(day)))
        tags = (["Work", "Meetings"] if work and age % 4 == 0 else [])
        if social:
            tags += ["Socialising", "Call"]
        if chores:
            tags += ["Household", "Cooking"]
        if tags:
            activities.append(dict(id=sample_id(f"activity-{day}"), date=str(day), tags=tags,
                                   duration="15–60 min", effort="Moderate", demands=["Thinking"],
                                   worth="Yes" if social else None, workLength=None, workPlace=None,
                                   **stamp(day)))
    for age, duration in ((1, None), (20, 3), (152, 4)):
        onset = end - timedelta(days=age)
        if onset < start:
            continue
        recovery = onset + timedelta(days=duration) if duration else None
        episodes.append(dict(id=sample_id(f"episode-{onset}"), start=str(onset),
                             end=str(recovery) if recovery else None,
                             approxStart=True, approxEnd=False, worst="Heavy fatigue", **stamp(end)))
    return dict(format="ChilliBook", version=1, days=days, checks=checks,
                activities=activities, episodes=episodes,
                prefs=dict(id=1, theme="system", pins=["Brain fog", "Heavy fatigue"]))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("--end", type=date.fromisoformat, default=date.today())
    parser.add_argument("--days", type=int, choices=[28, 365], default=365)
    args = parser.parse_args()
    data = generate(args.end, args.days)
    with args.output.open("x", encoding="utf-8") as stream:
        json.dump(data, stream, indent=2)
    print("Synthetic backup:", args.output)
    print(", ".join(f"{len(data[key])} {key}" for key in ["days", "checks", "activities", "episodes"]))

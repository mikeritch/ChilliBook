"""Compare two synthetic ChilliBook rehearsal exports without printing entries."""
import argparse
import json
from pathlib import Path


def normalized(path, *, candidate=False):
    with path.open(encoding="utf-8") as stream:
        data = json.load(stream)
    if data.get("format") != "ChilliBook" or data["version"] not in (1, 2, 3, 4, 5, 6, 7, 8):
        raise ValueError("Unsupported rehearsal export")
    version = data["version"]
    if candidate and version != 8:
        raise ValueError("Expected a format 8 export from the updated app")
    if version < 3:
        del data["episodes"]
        for day in data["days"]:
            del day["workOff"]
    elif "episodes" in data or any("workOff" in day for day in data["days"]):
        raise ValueError("Retired fields in rehearsal export")
    if version == 1:
        data["prefs"]["morningReminder"] = None
        data["prefs"]["eveningReminder"] = None
    if version < 4:
        del data["prefs"]["pins"]
    elif "pins" in data["prefs"]:
        raise ValueError("Retired symptom pins in format 4 export")
    if version < 5:
        data["prefs"]["workPin"] = None
        data["prefs"]["activityPins"] = {}
        for activity in data["activities"]:
            activity["workSetup"] = False
    if data["prefs"]["workPin"] is not None:
        for field in ("parts", "demands"):
            data["prefs"]["workPin"][field].sort()
    for pin in data["prefs"]["activityPins"].values():
        pin["demands"].sort()
    if version < 6:
        for day in data["days"]:
            day["noRests"] = None
        for activity in data["activities"]:
            for field in ("restKind", "restPeriod", "restEnergy"):
                activity[field] = None
    if version < 7:
        for table in ("courses", "regimens", "doses"):
            data[table] = []
    if version < 8:
        for day in data["days"]:
            for field in ("stress", "caffeineTimes", "alcoholTimes"):
                day[field] = None
    for day in data["days"]:
        for field in ("caffeineTimes", "alcoholTimes"):
            if day[field] is not None:
                day[field].sort()
    data["version"] = 8
    for table, keys in {"days": ("date",), "checks": ("date", "slot"),
                        "activities": ("id",), "courses": ("id",),
                        "regimens": ("id",), "doses": ("id",)}.items():
        rows = data[table]
        for row in rows:
            for key in ("tags", "demands", "context"):
                if key in row:
                    row[key].sort()
        rows.sort(key=lambda row: tuple(row[key] for key in keys))
    return data


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("before", type=Path)
    parser.add_argument("after", type=Path)
    args = parser.parse_args()
    before, after = normalized(args.before), normalized(args.after, candidate=True)
    if before != after:
        raise SystemExit("Mismatch in rehearsal exports. Inspect the synthetic fixture before release.")
    print("Every record, ID, date, value and preference matches the expected migration.")

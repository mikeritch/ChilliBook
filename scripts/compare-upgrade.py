"""Compare two synthetic ChilliBook rehearsal exports without printing entries."""
import argparse
import json
from pathlib import Path


def normalized(path):
    with path.open(encoding="utf-8") as stream:
        data = json.load(stream)
    if data.get("format") != "ChilliBook" or data["version"] not in (1, 2):
        raise ValueError("Unsupported rehearsal export")
    if data["version"] == 1:
        data["prefs"]["morningReminder"] = None
        data["prefs"]["eveningReminder"] = None
        data["version"] = 2
    data["prefs"]["pins"].sort()
    for table, keys in {"days": ("date",), "checks": ("date", "slot"),
                        "activities": ("id",), "episodes": ("id",)}.items():
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
    before, after = normalized(args.before), normalized(args.after)
    if before != after:
        raise SystemExit("Mismatch in rehearsal exports. Inspect the synthetic fixture before release.")
    print("Every record, ID, date, value and preference matches the expected migration.")

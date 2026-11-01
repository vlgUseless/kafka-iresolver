"""Download bounded public Jira selections and build the resolved-issue corpus."""

import argparse
import json
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JIRA_URL = "https://issues.apache.org/jira/rest/api/2/search"
RESOLVED_QUERY = "project = KAFKA AND resolution = Fixed ORDER BY resolved DESC, key DESC"
OPEN_QUERY = "project = KAFKA AND resolution = Unresolved AND type = Bug ORDER BY created DESC, key DESC"
FIELDS = "summary,description,resolution,status,priority,assignee,reporter,labels,comment,issuetype,created,updated"


def request_json(url):
    for attempt in range(5):
        try:
            with urllib.request.urlopen(url, timeout=90) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            if error.code != 429 and error.code < 500:
                raise
            if attempt == 4:
                raise
        except (urllib.error.URLError, TimeoutError):
            if attempt == 4:
                raise
        time.sleep(2 * (attempt + 1))
    raise RuntimeError("Исчерпаны попытки обращения к Jira")


def search(query, limit):
    issues = []
    while len(issues) < limit:
        parameters = urllib.parse.urlencode({
            "jql": query,
            "startAt": len(issues),
            "maxResults": min(100, limit - len(issues)),
            "fields": FIELDS,
        })
        page = request_json(f"{JIRA_URL}?{parameters}")
        issues.extend(page["issues"])
        print(f"Получено {len(issues)} из {page['total']}", flush=True)
        if not page["issues"] or len(issues) >= page["total"]:
            break
    return issues


def issue_text(issue):
    fields = issue["fields"]
    text = f"{fields.get('summary') or ''} {fields.get('description') or ''}"
    return " ".join(text.replace("|", " ").split())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--resolved-limit", type=int, default=1000)
    parser.add_argument("--open-limit", type=int, default=5)
    arguments = parser.parse_args()
    if arguments.resolved_limit <= 0 or arguments.open_limit <= 0:
        parser.error("Размеры выборок должны быть положительными")

    resolved = search(RESOLVED_QUERY, arguments.resolved_limit)
    opened = search(OPEN_QUERY, arguments.open_limit)
    snapshot = {
        "retrieved": datetime.now(timezone.utc).isoformat(),
        "resolvedJql": RESOLVED_QUERY,
        "openJql": OPEN_QUERY,
        "resolved": resolved,
        "opened": opened,
    }
    output = ROOT / "data"
    output.mkdir(exist_ok=True)
    (output / "snapshot.json").write_text(
        json.dumps(snapshot, ensure_ascii=False, indent=2), encoding="utf-8")
    lines = [f"{issue['key']}|{issue_text(issue)}" for issue in resolved]
    (output / "Issues_DataSet.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Снимок сохранён: {len(resolved)} исправленных и {len(opened)} открытых задач.")


if __name__ == "__main__":
    main()

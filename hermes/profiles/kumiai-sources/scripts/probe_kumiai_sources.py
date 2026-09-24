#!/usr/bin/env python3
"""probe_kumiai_sources.py — measure whether each jurisdiction's PRIMARY
legal source is actually readable, for the 管理組合 actor's catalog
(cloud-itonami/cloud-itonami-isic-6820, `realty.kumiai.facts`).

no_agent script: stdout becomes the job report. Empty stdout = silent.

WHY THIS EXISTS. The catalog's rule is that a jurisdiction gets a
`:resolutions` table only when its statute was read from the primary
source. Twice already the obstacle was not access but a body that
LOOKED like access:

  - `sso.agc.gov.sg/Act/BMSMA2004` answered HTTP 200 with a Page Not
    Found body for a whole day. The act had been retitled and the
    identifier was BSMA2004.
  - `normattiva.it` and `npc.gov.cn` answer 200 with a navigation frame
    and a news page.

So this probe classifies by CONTENT, never by status code, and it keeps
three outcomes apart that a naive check collapses into one:

  readable       decoded body contains every expected marker
  wrong-body     a 2xx whose body does not contain them
  undecodable    bytes arrived and could not be decoded -> NOT MEASURED

The third matters. The German page is ISO-8859-1, and shell `grep`
treats it as binary and reports zero matches with no indication that it
declined to look. A run that could not read must not report the same
thing as a run that read and found nothing.

WHAT IT WILL NOT DO. `AUS-NSW` and `FRA` sit behind bot-detection
interstitials. Issuing a plain GET is not evasion and is what happens
here. Defeating the challenge is evasion, is forbidden in this
workspace, and no entry may be moved on the strength of a body obtained
that way. Both carry `expect: bot-challenge`, so the expected result is
silent and only a change away from it is news.

Exit codes:
  0  scanned; findings (if any) on stdout
  2  COULD NOT MEASURE — every source errored (host offline). Distinct
     from 0 on purpose: a scan that reached nothing must not read like a
     scan that found nothing wrong.

Env:
  KUMIAI_SOURCES  path to sources.json  (default: alongside this file,
                  else the profile's scripts/ dir)
  KUMIAI_LEDGER   path to the jsonl ledger (default: profile workspace)
  KUMIAI_TIMEOUT  per-request seconds (default 45)
"""
import datetime
import json
import os
import re
import sys
import urllib.error
import urllib.request

HOME = os.path.expanduser("~")
SELF = "kumiai-sources"
HERE = os.path.dirname(os.path.abspath(__file__))

SOURCES = os.environ.get("KUMIAI_SOURCES") or os.path.join(HERE, "sources.json")
LEDGER = os.environ.get("KUMIAI_LEDGER") or os.path.join(
    HOME, ".hermes", "profiles", SELF, "workspace", "kumiai-source-ledger.jsonl")
TIMEOUT = float(os.environ.get("KUMIAI_TIMEOUT", "45"))

UA = ("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")

CHALLENGE_MARKERS = ("just a moment", "cf-browser-verification",
                     "attention required", "checking your browser",
                     "enable javascript and cookies to continue")


def decode(raw, headers):
    """Bytes -> text, or None when it genuinely could not be decoded.

    Charset comes from the header, then from a <meta charset>, then from
    a short list of encodings actually seen on these sites. Returning
    None is a real answer -- see the module docstring."""
    charset = None
    ctype = (headers.get("Content-Type") or "") if headers else ""
    m = re.search(r"charset=([\w\-]+)", ctype, re.I)
    if m:
        charset = m.group(1)
    if not charset:
        head = raw[:4096]
        m = re.search(br"charset=[\"']?([\w\-]+)", head, re.I)
        if m:
            charset = m.group(1).decode("ascii", "ignore")
    for enc in [charset, "utf-8", "iso-8859-1", "cp1252", "gb18030", "shift_jis"]:
        if not enc:
            continue
        try:
            return raw.decode(enc)
        except (LookupError, UnicodeDecodeError):
            continue
    return None


def classify(url, markers):
    """One candidate url -> (outcome, detail)."""
    req = urllib.request.Request(url, headers={
        "User-Agent": UA,
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "en-US,en;q=0.9",
    })
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT) as r:
            raw, headers, code = r.read(), r.headers, r.getcode()
    except urllib.error.HTTPError as e:
        try:
            raw, headers, code = e.read(), e.headers, e.code
        except Exception:
            return "http-%s" % e.code, "no body"
    except Exception as e:
        return "error", type(e).__name__
    text = decode(raw, headers)
    if text is None:
        # Bytes arrived and could not be read. This is NOT "markers absent".
        return "undecodable", "%d bytes" % len(raw)
    low = text.lower()
    if any(c in low for c in CHALLENGE_MARKERS):
        return "bot-challenge", "http %d, %d bytes" % (code, len(raw))
    if code >= 400:
        return "http-%d" % code, "%d bytes" % len(raw)
    missing = [m for m in markers if m not in text]
    if not missing:
        return "readable", "http %d, %d bytes" % (code, len(raw))
    return "wrong-body", "http %d, %d bytes, missing %s" % (
        code, len(raw), "/".join(missing[:2]))


def probe(source):
    """Best outcome across the candidate urls, and the url that gave it.

    'Best' is ordered so that a single readable candidate wins: the
    point of listing several is that the gap closes as soon as ONE of
    them serves the text."""
    rank = {"readable": 0, "wrong-body": 1, "bot-challenge": 2,
            "undecodable": 3, "error": 5}
    best = None
    for url in source["candidates"]:
        outcome, detail = classify(url, source["markers"])
        score = rank.get(outcome, 4)
        if best is None or score < best[0]:
            best = (score, outcome, detail, url)
        if outcome == "readable":
            break
    return {"outcome": best[1], "detail": best[2], "url": best[3]}


def previous():
    try:
        with open(LEDGER, encoding="utf-8") as f:
            lines = [l for l in f.read().splitlines() if l.strip()]
        return json.loads(lines[-1]) if lines else None
    except Exception:
        return None


def main():
    with open(SOURCES, encoding="utf-8") as f:
        cfg = json.load(f)
    sources = cfg["sources"]

    now = datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat()
    results = {}
    for s in sources:
        results[s["iso3"]] = probe(s)

    measured = [r for r in results.values() if r["outcome"] != "error"]
    row = {"at": now, "scanned": len(sources), "measured": len(measured),
           "results": {k: v["outcome"] for k, v in results.items()}}

    # Read the previous line BEFORE appending this one. Appending first
    # makes `previous()` return the row just written, `was != now` is
    # then never true, and the CHANGED branch can never fire -- a check
    # that cannot fire is indistinguishable from one that found nothing.
    prev = previous()

    os.makedirs(os.path.dirname(LEDGER), exist_ok=True)
    with open(LEDGER, "a", encoding="utf-8") as f:
        f.write(json.dumps(row, ensure_ascii=False) + "\n")

    if not measured:
        # Reached nothing. Say so with a code that is neither 0 nor 1.
        print("COULD NOT MEASURE: every source errored (%d/%d). "
              "This is not a clean scan." % (len(sources), len(sources)))
        return 2

    prev_res = (prev or {}).get("results", {})
    by_iso = {s["iso3"]: s for s in sources}

    findings = []
    for iso3, r in sorted(results.items()):
        s = by_iso[iso3]
        expected = s.get("expect")
        was = prev_res.get(iso3)
        now_o = r["outcome"]

        todo = s.get("todo")
        if now_o == "readable" and todo:
            # Either an unverified source that just became legible, or a
            # verified-but-intermittent one whose window is open right
            # now and still has something unread in it. Both are the
            # same instruction: go and read it while it answers.
            findings.append(
                "OPPORTUNITY %s: the primary source is READABLE now -- %s (%s). Statute: %s. TODO: %s"
                % (iso3, r["url"], r["detail"], s["statute"], todo))
        elif (s["state"] == "verified" and now_o != "readable"
              and expected != now_o and not s.get("intermittent")):
            findings.append(
                "REGRESSION %s: a source the catalog CITES is no longer readable -- %s -> %s (%s)."
                % (iso3, r["url"], now_o, r["detail"]))
        elif (was is not None and was != now_o and expected != now_o
              and not s.get("intermittent")):
            findings.append("CHANGED %s: %s -> %s (%s, %s)" % (iso3, was, now_o, r["url"], r["detail"]))

    # Evidence floor: always state what was scanned, so a silent run and
    # an unrun one are distinguishable in the ledger.
    summary = "SCANNED\t%d\tMEASURED\t%d\t%s" % (
        len(sources), len(measured),
        " ".join("%s=%s" % (k, v["outcome"]) for k, v in sorted(results.items())))

    if findings:
        print(summary)
        for line in findings:
            print(line)
    elif prev is None:
        print(summary)
        print("baseline recorded (first scan; nothing to compare against yet)")
    return 0


if __name__ == "__main__":
    sys.exit(main())

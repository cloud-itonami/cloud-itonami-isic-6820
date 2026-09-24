#!/usr/bin/env python3
"""kotoba_migration_tick.py — measure the 管理組合 actor's Kotoba migration
frontier, once per tick.

no_agent script: stdout becomes the job report. Empty stdout = silent.

WHAT IT WATCHES, and why each is here rather than in a person's memory:

  gate      `scripts/kotoba_native_acceptance.cljs` in the child repo —
            build AND acceptance with a JDK denied and traced. Its exit
            code is the answer: 0 pass, 1 fail, 2 could-not-measure.

  amu#835   native keyword equality is always false there
            (`(= :passed :passed)` -> 0 on aarch64-macos, 1 on wasm).
            `works_core.kotoba` is BLOCKED on native because of it. The
            gate already carries a probe that FAILS when the defect
            stops reproducing, so a fix arrives here as a red gate with
            a message naming what to unblock. This tick reports it.

  slices    which `kotoba/kumiai/*.kotoba` exist, and which targets each
            is qualified on, read from the module headers rather than
            remembered.

WHAT IT WILL NOT DO. It does not edit the catalog, unblock anything, or
run the migration. It reports; a person or an instructed agent acts.

THREE OUTCOMES, KEPT APART (the failure this repo keeps naming is a
check that could not run reporting what a clean one reports):

  0  ran; findings on stdout, silence when nothing changed
  1  the gate FAILED — a real regression, or amu#835 fixed and the
     native block now removable
  2  COULD NOT MEASURE — the checkout, the toolchain or nbb is absent.
     Deliberately neither 0 nor 1.

Env:
  KUMIAI_ROOT    superproject root (default: the workspace path below)
  KUMIAI_LEDGER  jsonl ledger (default: this profile's workspace)
"""
import datetime
import json
import os
import re
import subprocess
import sys

HOME = os.path.expanduser("~")
SELF = "kumiai-kotoba"
ROOT = os.environ.get("KUMIAI_ROOT", os.path.join(HOME, "github", "com-junkawasaki"))
CHILD = os.path.join(ROOT, "orgs", "cloud-itonami", "cloud-itonami-isic-6820")
KL = os.path.join(ROOT, "orgs", "kotoba-lang")
LEDGER = os.environ.get("KUMIAI_LEDGER") or os.path.join(
    HOME, ".hermes", "profiles", SELF, "workspace", "kotoba-migration-ledger.jsonl")
GATE = os.path.join("scripts", "kotoba_native_acceptance.cljs")

CLASSPATH = ":".join([
    "src", "scripts",
    os.path.join(KL, "langgraph", "src"),
    os.path.join(KL, "langchain", "src"),
    os.path.join(KL, "langchain-store", "src"),
])


def cannot(msg):
    print("COULD-NOT-MEASURE\t%s" % msg)
    sys.exit(2)


def run_gate():
    """The acceptance script's own verdict. Its exit code is the answer;
    stdout carries the evidence lines it prints."""
    try:
        r = subprocess.run(["nbb", "--classpath", CLASSPATH, GATE],
                           cwd=CHILD, capture_output=True, text=True, timeout=1800)
    except FileNotFoundError:
        cannot("nbb is not on PATH")
    except subprocess.TimeoutExpired:
        cannot("the acceptance gate did not finish within 30 minutes")
    return r.returncode, (r.stdout or "") + (r.stderr or "")


def parse(out):
    """Pull the gate's own measured lines out. Absent keys stay absent --
    a missing measurement must not read as a zero."""
    got = {}
    for key, pat in [
        ("native_self_check", r"NATIVE\t\S+\tself-check-failures\t(\d+)"),
        ("parity_cases", r"PARITY\tCASES\t(\d+)"),
        ("parity_disagreements", r"PARITY\tCASES\t\d+\tDISAGREEMENTS\t(\d+)"),
        ("wasm_works_failures", r"WASM\t\S+\tworks-core-self-check-failures\t(\d+)"),
        ("native_keyword_equality", r"NATIVE-KEYWORD-EQUALITY\t(\d+)"),
        ("jvm_invocations", r"JVM-INVOCATIONS\t(\d+)"),
    ]:
        m = re.search(pat, out)
        if m:
            got[key] = int(m.group(1))
    return got


def slices():
    """Which Kotoba modules exist and what each says it is qualified on.
    Read from the headers, so a module that changes its own claim shows
    up here rather than in nobody's notes."""
    d = os.path.join(CHILD, "kotoba", "kumiai")
    out = {}
    if not os.path.isdir(d):
        return out
    for name in sorted(os.listdir(d)):
        if not name.endswith(".kotoba"):
            continue
        try:
            with open(os.path.join(d, name), encoding="utf-8") as f:
                head = f.read(6000)
        except OSError:
            continue
        blocked = "BLOCKED" in head or "BLOCKED ON NATIVE" in head.upper()
        out[name] = "wasm-only" if blocked else "native+wasm"
    return out


def previous():
    try:
        with open(LEDGER, encoding="utf-8") as f:
            lines = [l for l in f.read().splitlines() if l.strip()]
        return json.loads(lines[-1]) if lines else None
    except Exception:
        return None


def main():
    if not os.path.isdir(CHILD):
        cannot("child checkout absent at %s" % CHILD)
    if not os.path.exists(os.path.join(CHILD, GATE)):
        cannot("the acceptance gate is not in the checkout: %s" % GATE)

    code, out = run_gate()
    measured = parse(out)
    sl = slices()

    # Evidence floor: a gate that printed none of its own measurements did
    # not run, whatever it returned.
    if not measured:
        cannot("the gate produced no measurement lines (exit %d): %s"
               % (code, out.strip()[:300]))

    now = datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat()
    row = {"at": now, "gate_exit": code, "measured": measured, "slices": sl}

    prev = previous()
    os.makedirs(os.path.dirname(LEDGER), exist_ok=True)
    with open(LEDGER, "a", encoding="utf-8") as f:
        f.write(json.dumps(row, ensure_ascii=False) + "\n")

    findings = []

    # amu#835 fixed: the gate says so itself, and it is the whole reason
    # works_core is blocked on native.
    if measured.get("native_keyword_equality") == 1:
        findings.append(
            "UNBLOCK: native keyword equality now answers correctly — kotoba-lang/amu#835 "
            "appears FIXED. Add aarch64-macos to kotoba/kumiai/works_core.kotoba, delete the "
            "probe in scripts/kotoba_native_acceptance.cljs, and re-run the gate.")

    if code == 1 and measured.get("native_keyword_equality") != 1:
        findings.append("GATE FAILED (exit 1): " + " | ".join(
            l for l in out.splitlines() if l.startswith(("FAIL", "  MISMATCH"))) or "see the ledger")

    if prev:
        pm = prev.get("measured", {})
        for k, v in sorted(measured.items()):
            if k in pm and pm[k] != v:
                findings.append("CHANGED %s: %s -> %s" % (k, pm[k], v))
        ps = prev.get("slices", {})
        for name, q in sorted(sl.items()):
            if name not in ps:
                findings.append("NEW SLICE %s (%s)" % (name, q))
            elif ps[name] != q:
                findings.append("SLICE %s: %s -> %s" % (name, ps[name], q))

    summary = "GATE\texit=%d\t%s\tSLICES\t%s" % (
        code,
        " ".join("%s=%s" % (k, v) for k, v in sorted(measured.items())),
        " ".join("%s:%s" % (n, q) for n, q in sorted(sl.items())) or "none")

    if findings:
        print(summary)
        for f in findings:
            print(f)
    elif prev is None:
        print(summary)
        print("baseline recorded (first tick; nothing to compare against yet)")

    return 1 if code == 1 else 0


if __name__ == "__main__":
    sys.exit(main())

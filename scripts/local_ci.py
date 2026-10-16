"""Offline/local-device validation with per-run logs and a fail-closed JSON report."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
NATIVE_CLASS = "com.looktube.app.NativeLibraryMechanicsTest"
DEBUG_PACKAGE = "com.looktube.app.highspec.debug"
EVIDENCE_NAMES = ("group-expanded", "group-mid-contraction", "group-collapsed", "outside-dismissal", "large-text-collapsed")


class StepFailure(RuntimeError):
    pass


def validate_instrumentation(output):
    # adb can exit zero even when instrumentation crashes or JUnit fails.
    if re.search(r"FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=|INSTRUMENTATION_ABORTED", output):
        raise StepFailure("Native instrumentation reported a failure; inspect native-tests.log")
    counts = re.findall(r"^OK \((\d+) tests?\)\s*$", output, re.MULTILINE)
    if counts != ["3"] or not re.search(r"^INSTRUMENTATION_CODE: -1\s*$", output, re.MULTILINE):
        raise StepFailure("Expected successful completion of all 3 native tests; missing/partial runs do not pass")


class Run:
    def __init__(self, folder, lane, run_id):
        self.folder = folder
        folder.mkdir(parents=True, exist_ok=False)
        self.summary = {"run_id": run_id, "lane": lane, "status": "running", "started_utc": datetime.now(timezone.utc).isoformat(), "steps": []}
        self.save()

    def save(self):
        temporary = self.folder / "summary.tmp"
        temporary.write_text(json.dumps(self.summary, indent=2) + "\n", encoding="utf-8")
        temporary.replace(self.folder / "summary.json")

    def step(self, name, command, validator=None, required=True, timeout=1200):
        print(f"[{name}] running", flush=True)
        started = time.monotonic()
        log = self.folder / f"{name}.log"
        item = {"name": name, "command": [str(arg) for arg in command], "log": log.name, "required": required, "timeout_seconds": timeout, "status": "running"}
        self.summary["steps"].append(item)
        self.save()
        try:
            with log.open("wb") as stream:
                result = subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, check=False, timeout=timeout)
            item["exit_code"] = result.returncode
            if result.returncode:
                raise StepFailure(f"{name} exited {result.returncode}; inspect {log}")
            output = log.read_text(encoding="utf-8", errors="replace")
            if validator:
                validator(output)
            item["status"] = "passed"
            return output
        except Exception as error:
            item["status"] = "failed"
            item["error"] = str(error)
            if required:
                raise StepFailure(str(error)) from error
        except KeyboardInterrupt:
            item["status"] = "interrupted"
            raise
        finally:
            item["seconds"] = round(time.monotonic() - started, 2)
            self.save()
            print(f"[{name}] {item['status']}", flush=True)


def require_device(output):
    if output.strip() != "device":
        raise StepFailure("The explicitly selected device must be online and authorized")


def require_api(output):
    if not output.strip().isdigit() or int(output.strip()) < 35:
        raise StepFailure("The highspec native lane requires Android API 35 or newer")


def require_abi(output):
    if "arm64-v8a" not in output.strip().split(","):
        raise StepFailure("The highspec native lane requires arm64-v8a")


def execute(run, serial=None):
    wrapper = str(ROOT / ("gradlew.bat" if sys.platform == "win32" else "gradlew"))
    # Standard-library runner tests use this same Python interpreter from Gradle.
    gradle = [wrapper, f"-PlooktubePython={sys.executable}", "--console=plain"]
    if run.summary["lane"] == "fast":
        run.step("fast", gradle + ["verifyFast"])
        return
    if run.summary["lane"] == "full":
        run.step("full", gradle + ["verifyAllLocal"])
        return
    if not serial:
        raise StepFailure("Device lane requires an explicit --serial")
    run.summary["device_serial"] = serial
    run.save()
    adb = ["adb", "-s", serial]
    run.step("device-online", adb + ["get-state"], require_device, timeout=30)
    run.step("device-api", adb + ["shell", "getprop", "ro.build.version.sdk"], require_api)
    run.step("device-abi", adb + ["shell", "getprop", "ro.product.cpu.abilist"], require_abi)
    run.step("native-build", gradle + [":app:verifyVariantContracts", ":app:assembleHighspecDebug", ":app:assembleHighspecDebugAndroidTest"])
    apks = [ROOT / "app/build/outputs/apk/highspec/debug/app-highspec-debug.apk",
            ROOT / "app/build/outputs/apk/androidTest/highspec/debug/app-highspec-debug-androidTest.apk"]
    run.summary["apks"] = []
    for path in apks:
        with path.open("rb") as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        run.summary["apks"].append({"path": str(path.relative_to(ROOT)), "sha256": digest})
    run.save()
    for index, apk in enumerate(apks):
        run.step(f"install-debug-{index}", adb + ["install", "-r", str(apk)])
    remote = f"/sdcard/Android/data/{DEBUG_PACKAGE}/files/local-ci/{run.summary['run_id']}"
    pull = adb + ["pull", remote, str(run.folder / "device-evidence")]
    try:
        run.step("native-tests", adb + ["shell", "am", "instrument", "-w", "-r", "-e", "class", NATIVE_CLASS,
                 "-e", "localCiRunId", run.summary["run_id"], f"{DEBUG_PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner"], validate_instrumentation, timeout=240)
    except StepFailure:
        run.step("failure-evidence", pull, required=False)
        raise
    run.step("native-evidence", pull)
    missing = [name for name in EVIDENCE_NAMES if not (run.folder / "device-evidence" / f"{name}.png").is_file()]
    if missing:
        raise StepFailure("Missing current-run native screenshots: " + ", ".join(missing))
    run.summary["native_tests"] = 3
    run.summary["evidence"] = [f"device-evidence/{name}.png" for name in EVIDENCE_NAMES]
    run.save()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("lane", choices=("fast", "full", "device"))
    parser.add_argument("--serial", help="Required only for device; never auto-selects a phone")
    args = parser.parse_args()
    if (args.lane == "device") != bool(args.serial):
        parser.error("Use --serial with the device lane only")
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dt%H%M%Sz").lower() + "-" + uuid.uuid4().hex[:8]
    run = Run(ROOT / "artifacts/local-ci" / run_id, args.lane, run_id)
    print(f"Report: {run.folder / 'summary.json'}", flush=True)
    result = 1
    try:
        run.summary["source_commit"] = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
        run.summary["working_tree_dirty"] = bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT, text=True).strip())
        run.save()
        execute(run, args.serial)
        run.summary["status"] = "passed"
        result = 0
    except Exception as error:
        run.summary["status"] = "failed"
        run.summary["error"] = str(error)
        print(str(error), file=sys.stderr)
    except KeyboardInterrupt:
        run.summary["status"] = "interrupted"
    finally:
        run.summary["finished_utc"] = datetime.now(timezone.utc).isoformat()
        run.save()
    print(f"Local CI: {run.summary['status']}. Report: {run.folder / 'summary.json'}", flush=True)
    return result


if __name__ == "__main__":
    sys.exit(main())

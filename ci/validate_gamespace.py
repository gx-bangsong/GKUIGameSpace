#!/usr/bin/env python3
"""Dependency-free CI checks for the GameSpace feature integration.
The Android app depends on ROM-only platform libraries which are intentionally not
stored in this repository.  These checks still validate the feature contract and
permission wiring on every GitHub-hosted runner.  When system_libs/*.jar is supplied,
the workflow additionally runs the complete Gradle test and assemble tasks.
"""
from __future__ import annotations
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
REQUIRED_FILES = [
    "app/src/main/java/io/chaldeaprjkt/gamespace/data/GameSpaceMode.kt",
    "app/src/main/java/io/chaldeaprjkt/gamespace/data/GameTimer.kt",
    "app/src/main/java/io/chaldeaprjkt/gamespace/data/TouchAction.kt",
    "app/src/main/java/io/chaldeaprjkt/gamespace/data/ComboSequence.kt",
    "app/src/main/java/io/chaldeaprjkt/gamespace/data/VideoToolboxState.kt",
    "app/src/main/java/io/chaldeaprjkt/gamespace/gamebar/VideoAppDetector.kt",
    "app/src/main/java/io/chaldeaprjkt/gamespace/gamebar/GameSpaceModeManager.kt",
    "app/src/main/java/io/chaldeaprjkt/gamespace/gamebar/GameTimerManager.kt",
    "app/src/main/java/io/chaldeaprjkt/gamespace/gamebar/GameTimerView.kt",
    "app/src/main/java/io/chaldeaprjkt/gamespace/gamebar/TimerEditDialog.kt",
    "app/src/main/java/io/chaldeaprjkt/gamespace/gamebar/GameTimerModule.kt",
    "app/src/main/java/io/chaldeaprjkt/gamespace/gamebar/ComboRecorder.kt",
    "app/src/main/java/io/chaldeaprjkt/gamespace/gamebar/ComboExecutor.kt",
    "app/src/main/java/io/chaldeaprjkt/gamespace/gamebar/AutoComboModule.kt",
    "app/src/main/java/io/chaldeaprjkt/gamespace/gamebar/ComboTriggerView.kt",
    "app/src/main/java/io/chaldeaprjkt/gamespace/gamebar/ScreenOffAudioManager.kt",
    "app/src/main/java/io/chaldeaprjkt/gamespace/gamebar/VideoTimerCloseManager.kt",
    "app/src/main/java/io/chaldeaprjkt/gamespace/gamebar/VideoToolboxView.kt",
    "app/src/main/java/io/chaldeaprjkt/gamespace/gamebar/VideoToolboxModule.kt",
    "app/src/main/java/io/chaldeaprjkt/gamespace/gamebar/GameSpaceToolbar.kt",
]
REQUIRED_TEXT = {
    "app/src/main/java/io/chaldeaprjkt/gamespace/gamebar/GameTimerManager.kt": (
        "MutableSharedFlow",
        "SystemClock.elapsedRealtime",
    ),
    "app/src/main/java/io/chaldeaprjkt/gamespace/gamebar/ComboExecutor.kt": (
        "InputManager.INJECT_INPUT_EVENT_MODE_ASYNC",
        "MotionEvent.ACTION_DOWN",
        "MotionEvent.ACTION_UP",
    ),
    "app/src/main/java/io/chaldeaprjkt/gamespace/gamebar/ComboRecorder.kt": (
        "monitorGestureInput",
        "InputEventReceiver",
    ),
    "app/src/main/java/io/chaldeaprjkt/gamespace/gamebar/AutoComboModule.kt": (
        "CREATE TABLE $SEQUENCES",
        "CREATE TABLE $ACTIONS",
        "SQLiteOpenHelper",
    ),
    "app/src/main/java/io/chaldeaprjkt/gamespace/gamebar/GameSpaceService.kt": (
        "TaskStackListener",
        "onForegroundAppChanged",
    ),
}
REQUIRED_PERMISSIONS = (
    "android.permission.INJECT_EVENTS",
    "android.permission.MONITOR_INPUT",
    "android.permission.SYSTEM_ALERT_WINDOW",
    "android.permission.WRITE_SETTINGS",
    "android.permission.WRITE_SECURE_SETTINGS",
    "android.permission.MEDIA_CONTENT_CONTROL",
    "android.permission.DEVICE_POWER",
    "android.permission.STATUS_BAR_SERVICE",
)
def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")
def check_xml(relative: str) -> None:
    ET.parse(ROOT / relative)
    print(f"XML OK: {relative}")
def main() -> int:
    failures: list[str] = []
    for relative in REQUIRED_FILES:
        if not (ROOT / relative).is_file():
            failures.append(f"missing required file: {relative}")
    gradle = read("app/build.gradle.kts")
    if "minSdk = 34" not in gradle:
        failures.append("app/build.gradle.kts must keep minSdk = 34")
    manifest = read("app/src/main/AndroidManifest.xml")
    whitelist = read("app/src/main/privapp_whitelist_io.chaldeaprjkt.gamespace.xml")
    for permission in REQUIRED_PERMISSIONS:
        if permission not in manifest:
            failures.append(f"manifest is missing {permission}")
        if permission not in whitelist:
            failures.append(f"priv-app whitelist is missing {permission}")
    for relative, needles in REQUIRED_TEXT.items():
        source = read(relative)
        for needle in needles:
            if needle not in source:
                failures.append(f"{relative} is missing required text: {needle}")
    for relative in REQUIRED_FILES:
        path = ROOT / relative
        if path.exists():
            for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
                if line.rstrip() != line:
                    failures.append(f"trailing whitespace: {relative}:{line_number}")
    for relative in (
        "app/src/test/java/io/chaldeaprjkt/gamespace/data/GameSpaceModeTest.kt",
        "app/src/test/java/io/chaldeaprjkt/gamespace/data/GameTimerTest.kt",
        "app/src/test/java/io/chaldeaprjkt/gamespace/data/ComboSequenceTest.kt",
        "app/src/test/java/io/chaldeaprjkt/gamespace/gamebar/VideoAppDetectorTest.kt",
    ):
        if not (ROOT / relative).is_file():
            failures.append(f"missing test file: {relative}")
    if failures:
        print("GameSpace validation failed:", file=sys.stderr)
        for failure in failures:
            print(f"  - {failure}", file=sys.stderr)
        return 1
    print("GameSpace source, model, input, timer, video and permission checks passed.")
    return 0
if __name__ == "__main__":
    raise SystemExit(main())


"""Populate only an explicitly selected debug install with offline layout fixtures.

No production data or credentials are read. The deliberately invalid feed endpoint
cannot fetch a real library; the seeded cache remains available for browsing.
"""

import argparse
import json
import shlex
import subprocess
import xml.etree.ElementTree as ET


def preferences(values):
    root = ET.Element("map")
    for key, value in values.items():
        ET.SubElement(root, "string", name=key).text = value
    return ET.tostring(root, encoding="utf-8", xml_declaration=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--package", required=True, choices=(
        "com.looktube.app.debug", "com.looktube.app.highspec.debug"))
    args = parser.parse_args()

    def adb(*command, **kwargs):
        return subprocess.run(["adb", "-s", args.serial, *command], check=True, **kwargs)

    # run-as must succeed; do not fall back to privileged access or another app.
    adb("shell", "run-as", args.package, "id", stdout=subprocess.DEVNULL)
    adb("shell", "am", "force-stop", args.package)
    feed = "https://example.invalid/looktube-layout-fixture"
    videos = []
    groups = [("Grubbsnax Premium", 3), ("A longer show title for testing wrapped text", 12),
              ("Single video group", 1), ("Large collection", 24)]
    for group_index, (show, count) in enumerate(groups):
        for index in range(count):
            video_id = f"layout-{group_index}-{index}"
            videos.append(dict(id=video_id, title=f"Layout fixture episode {index + 1:02d}",
                description="Local debug fixture. Check the group boundary, description wrapping, and collapse controls. " * 3,
                is_premium=True, feed_category="Debug fixture", playback_url=None, series_title=show,
                thumbnail_url=f"android.resource://{args.package}/drawable/library_fixture_thumb_{index % 4 + 1}",
                published_at_epoch_millis=1_700_000_000_000 - len(videos) * 100_000, duration_seconds=1800))
    snapshot = dict(feed_url=feed, videos=videos,
                    last_successful_sync_summary="LOCAL DEBUG FIXTURE — no account data")
    engagement = {video["id"]: dict(video_id=video["id"], manual_watch_state="Watched")
                  for index, video in enumerate(videos) if index % 3 != 1}
    files = {
        "looktube.feed.config.xml": {"feed_url": feed},
        "looktube.synced.library.xml": {"snapshot_json": json.dumps(snapshot)},
        "looktube.video.engagement.xml": {"records_json": json.dumps(engagement)},
    }
    for filename, values in files.items():
        script = f"mkdir -p shared_prefs && cat > shared_prefs/{filename}"
        remote = shlex.join(["run-as", args.package, "sh", "-c", script])
        adb("shell", remote, input=preferences(values))
    print(f"Seeded {len(videos)} fixture videos in {args.package}; normal app untouched.")
    adb("shell", "am", "start", "-n", f"{args.package}/com.looktube.app.MainActivity")


if __name__ == "__main__":
    main()

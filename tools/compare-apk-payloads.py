"""Verify the packaging optimization preserves every native OCR/model payload."""
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path


def payloads(path):
    with zipfile.ZipFile(path) as archive:
        result = {}
        for entry in archive.infolist():
            if entry.filename.startswith(("lib/", "assets/mlkit-")):
                result[entry.filename] = {
                    "sha256": hashlib.sha256(archive.read(entry)).hexdigest(),
                    "bytes": entry.file_size,
                    "packed_bytes": entry.compress_size,
                    "method": entry.compress_type,
                }
        return result


def main():
    root = Path(__file__).resolve().parents[1]
    before = root.parent / "YuejiV4.6.3/dist/Yueji-4.6.3-debug.apk"
    after = root / "app/build/outputs/apk/debug/app-debug.apk"
    original, compact = payloads(before), payloads(after)
    assert original and original.keys() == compact.keys(), "OCR/ABI payload set changed"
    changed = [name for name in original
               if (original[name]["sha256"], original[name]["bytes"])
               != (compact[name]["sha256"], compact[name]["bytes"])]
    assert not changed, f"OCR payload changed: {changed}"
    abis = sorted({name.split("/")[1] for name in compact if name.startswith("lib/")})
    assert abis == ["arm64-v8a", "armeabi-v7a", "x86", "x86_64"], abis
    assert all(value["method"] == zipfile.ZIP_DEFLATED
               for name, value in compact.items() if name.startswith("lib/"))
    assert after.stat().st_size < before.stat().st_size, "APK did not shrink"
    with zipfile.ZipFile(after) as archive:
        assert not any(name.endswith(("fund-purchase-detail.jpg", "bank-income-v461.jpg"))
                       for name in archive.namelist()), "Private test images leaked into main APK"
        for entry in archive.infolist():
            if re.fullmatch(r"classes\d*\.dex", entry.filename):
                dex = archive.read(entry)
                assert not any(name in dex for name in (
                    b"TestDocumentsProvider", b"TestScreenshotProvider", b"V463UiTest"))
    report = {
        "baseline": str(before),
        "optimized": str(after),
        "baseline_bytes": before.stat().st_size,
        "optimized_bytes": after.stat().st_size,
        "saved_bytes": before.stat().st_size - after.stat().st_size,
        "saved_percent": round(100 * (1 - after.stat().st_size / before.stat().st_size), 2),
        "cpu_architectures": abis,
        "identical_ocr_payload_count": len(original),
        "changed_ocr_payloads": changed,
        "payloads": compact,
    }
    destination = root / "docs/validation/v466-payload-comparison.json"
    destination.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key != "payloads"},
                     indent=2, ensure_ascii=True))


if __name__ == "__main__":
    main()

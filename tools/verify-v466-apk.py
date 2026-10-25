"""Verify the final package, with no private test pictures or old application label."""
import hashlib
import json
import re
import zipfile
from pathlib import Path


root = Path(__file__).resolve().parents[1]
apk = root / "app/build/outputs/apk/debug/app-debug.apk"
with zipfile.ZipFile(apk) as archive:
    names = archive.namelist()
    private = ("fund-purchase-detail.jpg", "bank-income-v461.jpg", "bank-amount-v461.jpg")
    assert not any(name.endswith(private) for name in names), "Private fixture in main package"
    obsolete = "\u4f59\u989d\u8bb0"
    for name in names:
        if re.fullmatch(r"classes\d*\.dex", name) or name == "resources.arsc":
            blob = archive.read(name)
            assert obsolete.encode("utf-8") not in blob and obsolete.encode("utf-16le") not in blob
            if name.endswith(".dex"):
                assert not any(test in blob for test in (
                    b"TestDocumentsProvider", b"TestScreenshotProvider", b"V466UiTest",
                    b"AccountCashUiTest", b"IconCropView",
                )), "Test/custom-crop implementation in main DEX"
    resources = [
        "res/mipmap-anydpi-v26/icon_petals.xml",
        "res/drawable/ic_petals_foreground.xml",
        "res/drawable/ic_stat_petals.xml",
    ]
    assert all(name in names for name in resources), "Missing fresh two-petal icon"

result = {
    "apk": str(apk),
    "bytes": apk.stat().st_size,
    "sha256": hashlib.sha256(apk.read_bytes()).hexdigest().upper(),
    "main_package_has_no_test_classes_or_private_fixtures": True,
    "obsolete_app_name_absent_from_dex_and_resources": True,
    "fresh_two_petal_resources_present": resources,
}
(root / "docs/validation/v466-package-check.json").write_text(
    json.dumps(result, indent=2, ensure_ascii=False), encoding="utf-8"
)
print(json.dumps(result, indent=2, ensure_ascii=True))

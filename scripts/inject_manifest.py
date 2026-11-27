#!/usr/bin/env python3
"""把 TraceInitializer provider 注入 legado 的 AndroidManifest.xml。"""
import sys
from pathlib import Path

PROVIDER = """        <provider
            android:name="io.legado.app.ui.book.read.trace.TraceInitializer"
            android:authorities="${applicationId}.trace"
            android:exported="false" />
"""


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: inject_manifest.py <AndroidManifest.xml>")
        return 2
    path = Path(sys.argv[1])
    text = path.read_text(encoding="utf-8")
    if "TraceInitializer" in text:
        print("already injected")
        return 0
    marker = "</application>"
    if marker not in text:
        print("cannot find </application>")
        return 1
    text = text.replace(marker, PROVIDER + marker, 1)
    path.write_text(text, encoding="utf-8")
    print("injected")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

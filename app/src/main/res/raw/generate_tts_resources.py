#!/usr/bin/env python3
from __future__ import annotations

import json
from pathlib import Path

from gtts import gTTS

MESSAGES_PATH = Path("tts_unavailable_messages.json")
OUTPUT_DIR = Path(".")
FILE_PREFIX = "tts_unavailable"
SLOW = False


def main() -> int:
    # utf-8-sig handles normal UTF-8 files and UTF-8 with BOM.
    data = json.loads(MESSAGES_PATH.read_text(encoding="utf-8-sig"))
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    for lang, text in data.items():
        # Output example: tts_unavailable_de.mp3
        out_file = OUTPUT_DIR / f"{FILE_PREFIX}_{lang}.mp3"
        tts = gTTS(text=text.strip(), lang=lang, slow=SLOW)
        tts.save(str(out_file))
        print(f"Generated: {out_file}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

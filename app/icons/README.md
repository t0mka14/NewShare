# Application icons

Installer and launcher icons for `compose.desktop.nativeDistributions` in `app/build.gradle.kts`
(`windows.iconFile`, `macOS.iconFile`, `linux.iconFile`). The running window's icon is set
separately in `Main.kt` from the same source image on the classpath.

All three files are generated from `app/src/main/resources/drawable/sami_trans.png` (padded to a
square with a transparent background, then scaled):

| File | Platform | Sizes |
|---|---|---|
| `sami.ico` | Windows (`.msi`, `.exe`) | 16, 24, 32, 48, 64, 128, 256 px |
| `sami.icns` | macOS (`.dmg`, `.app`) | 16 – 1024 px |
| `sami.png` | Linux (`.deb`) | 512 px |

To regenerate after changing the logo (needs Pillow):

```bash
python3 - <<'PY'
from PIL import Image
src = Image.open("app/src/main/resources/drawable/sami_trans.png").convert("RGBA")
w, h = src.size; side = max(w, h)
sq = Image.new("RGBA", (side, side), (0, 0, 0, 0)); sq.paste(src, ((side - w) // 2, (side - h) // 2))
r = lambda n: sq.resize((n, n), Image.LANCZOS)
r(256).save("app/icons/sami.ico", format="ICO", sizes=[(s, s) for s in [16, 24, 32, 48, 64, 128, 256]])
r(1024).save("app/icons/sami.icns", format="ICNS", sizes=[(s, s) for s in [16, 32, 64, 128, 256, 512, 1024]])
r(512).save("app/icons/sami.png", format="PNG")
PY
```

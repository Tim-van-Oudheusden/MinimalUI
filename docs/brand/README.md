# Brand assets

Artwork for the repository, app stores and announcements. Every asset follows [`BRANDING.md`](../../BRANDING.md):
black and white, the selected row as an inverted pill, plain words.

The SVG files are the masters. Their text is outlined (converted to shapes), so they look the same everywhere, with or
without the font installed. The PNG and PDF files are exports of those SVGs at each platform's required size.

## The idea

The launcher's whole interface is one list with one selected row, so that list is the brand. The icon, the banners and
the social images all show it, drawn with the app's own metrics from `RowMetrics.kt`: 24 sp labels, 44 dp rows, and a
fully rounded pill padded 14 dp × 6 dp around the selected label. Rows on the phone screen are spaced the way
`listRhythm` spaces them, with the leftover height shared between whole rows.

- **Icon.** Three rows: a label, the selected pill with its label cut out, then a shorter label. The cut-out is
  transparent rather than painted black, so the monochrome icon on Android 13 and newer keeps it.
- **Wordmark.** The icon's glyph next to "MinimalUI" in Roboto Medium, with the glyph as tall as the capitals.
- **Type.** Roboto, the default system font on stock Android: Regular for app labels and body text, Medium for headlines.
  Until a dedicated typeface is chosen (BRANDING.md §6), this matches what most people see in the launcher itself.
- **Colour.** Pure black and white. Greys (`#9A9A9A` secondary text, `#5C5C5C` captions and keys, `#2E2E2E` rules)
  appear only in marketing layouts, never in the app.
- **Honesty.** The phone is a generic keyboard phone, not a real device, so no artwork implies that a phone is
  supported (BRANDING.md §5). The app names are generic system apps, listed alphabetically as the launcher sorts them.
  "Type to filter" shows real behaviour: typing `ca` puts `ca_` in the top row and selects the first match.

## Files

| File                                                                                   | Size              | Use                                                                    |
| -------------------------------------------------------------------------------------- | ----------------- | ---------------------------------------------------------------------- |
| [`icon/icon.svg`](icon/icon.svg)                                                       | 512 × 512         | Master icon, full-bleed square (stores apply their own mask)           |
| [`icon/icon-512.png`](icon/icon-512.png)                                               | 512 × 512         | Google Play and F-Droid listing icon (32-bit PNG)                      |
| [`icon/icon-1024.png`](icon/icon-1024.png), [`icon-192.png`](icon/icon-192.png)        | 1024², 192²       | Large and small exports, e.g. avatars and favicons                     |
| [`icon/icon-foreground.svg`](icon/icon-foreground.svg)                                 | 108 × 108         | Glyph alone on the adaptive-icon grid; mirrors `ic_launcher_foreground` |
| [`icon/icon-monochrome.svg`](icon/icon-monochrome.svg)                                 | 108 × 108         | Black glyph for light backgrounds                                      |
| [`icon/icon-masks.png`](icon/icon-masks.png)                                           | 960 × 390         | Preview under square, squircle and circle launcher masks               |
| [`logo/wordmark-white.svg`](logo/wordmark-white.svg) / [`.png`](logo/wordmark-white.png) | 653 × 131         | Wordmark for dark backgrounds (transparent)                            |
| [`logo/wordmark-black.svg`](logo/wordmark-black.svg) / [`.png`](logo/wordmark-black.png) | 653 × 131         | Wordmark for light backgrounds (transparent)                           |
| [`banner/readme-banner.svg`](banner/readme-banner.svg)                                 | 1280 × 320        | Header of the project README (PNG export at 2×)                        |
| [`banner/play-feature-graphic.png`](banner/play-feature-graphic.png)                   | 1024 × 500        | Google Play feature graphic (24-bit PNG, no alpha)                     |
| [`social/social-preview.png`](social/social-preview.png)                               | 1280 × 640        | GitHub social preview; also fits Open Graph and X/Twitter large cards  |
| [`social/social-square.png`](social/social-square.png)                                 | 1080 × 1080       | Square posts (Mastodon, Instagram, LinkedIn, forums)                   |
| [`social/social-portrait-filter.png`](social/social-portrait-filter.png)               | 1080 × 1350       | 4:5 portrait post showing type-to-filter                               |
| [`social/social-story.png`](social/social-story.png)                                   | 1080 × 1920       | Stories and shorts; key content kept clear of the top and bottom 270 px |
| [`poster/poster-a3.pdf`](poster/poster-a3.pdf)                                         | A3, vector        | Print; scales down to A4 without loss                                  |
| [`poster/poster-a4-300dpi.png`](poster/poster-a4-300dpi.png)                           | 2480 × 3507       | Raster poster, A4 at 300 dpi                                           |

Every image has an SVG master next to it under the same name.

## Using them

- **GitHub social preview:** repository **Settings → General → Social preview → Edit → Upload an image**, then choose
  `social/social-preview.png`. GitHub cannot read this from the repository; a maintainer has to upload it once.
- **README:** the banner is linked as the SVG, so it stays sharp on any screen.
- **Google Play:** `icon/icon-512.png` is the app icon and `banner/play-feature-graphic.png` the feature graphic. Phone
  screenshots should come from the app itself, not from these mock-ups.
- **F-Droid:** if the repository adopts fastlane metadata, copy `icon/icon-512.png` to
  `fastlane/metadata/android/en-US/images/icon.png` and the feature graphic to `featureGraphic.png` in the same folder.

## Changing them

Edit the SVG master, then re-export the PNG at the size in the table (any SVG renderer will do, e.g. a browser,
Inkscape or `rsvg-convert`). Keep the in-app icon in step: `app/src/main/res/drawable/ic_launcher_foreground.xml` has
the same path data as `icon/icon-foreground.svg`.

When a mock-up needs to change, for example because the launcher's look changes, follow the rules above: app metrics
from `RowMetrics.kt`, generic app names in alphabetical order, a generic phone, and no claims the README does not make.

The outlined text is [Roboto](https://github.com/googlefonts/roboto-2), © Google, under the Apache License 2.0.

---
name: publishing-app-news
description: Writing and updating news about TrainingRecorder for the author's cycling website (a Wagtail site, three languages). Use when asked to publish, announce, or update information about the app on the site.
---

# Publishing news about the app

The author runs a cycling website built on **Wagtail**, in a separate repository
(`cycling-site`). Material about this app is staged there under `tmp/wearos/`
before being pasted into the CMS.

## The content model

`news.NewsPage` has two fields that matter:

- `intro` - plain text, **up to 500 characters**. Write it to fit, and note the
  actual count.
- `body` - a StreamField of text, image and embed blocks.

Keep the two separate in the staged files, split under headings, so each can be
pasted straight into its field.

## Languages

The site runs three: **ru, kk, en** (`WAGTAIL_CONTENT_LANGUAGES` in
`cycling_site/settings/base.py`). Write all three, as separate files
(`news-ru.md`, `news-kk.md`, `news-en.md`). They are translations of the same
piece, not three different pieces.

## What the staged folder holds

- `news-ru.md`, `news-kk.md`, `news-en.md` - title options, intro, body,
  suggested tags, and screenshot captions.
- `facts.md` - every checkable number with the file it came from
  (`FieldCatalogue`, `SportCatalogue`, the build files). This is what keeps the
  posts honest when they are updated months later.
- `README.md` - what is in the folder and how the pieces map to the CMS fields.
- `screenshots/` - watch shots at the device resolution, phone shots, each named
  so the caption list can refer to it.

## Tone

The app is young and is still being fixed. The posts say so: an early version,
not on Google Play, installed from an APK on GitHub Releases, and bug reports
are the most useful help. Do not write promises that will have to be taken back.

## Keeping it current

The numbers go stale on every release. When updating after a release, refresh in
all three languages plus `facts.md`:

- the version,
- the field count and the category count (count them out of `FieldCatalogue`
  and `FieldCategory` rather than trusting the old text),
- anything a release changed about how a feature actually behaves.

Take screenshots on the emulators (see the `wear-emulator-debugging` skill), and
say in the README which device and locale they were taken on, since the author
may want them re-shot in Russian.

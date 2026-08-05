# Add Qi Scan Manga Source

This plan outlines the steps to add "Qi Scan" (qimanga.com) as a new manga source to the Saikou project.

## User Review Required

> [!NOTE]
> The source will use the official API of `qimanga.com` (which seems to be the current site for Qi Scan) for better reliability and performance compared to HTML parsing.

## Proposed Changes

### [Parser Module]

#### [NEW] [QiScan.kt](file:///Users/sanalreghunath/StudioProjects/Saikou/app/src/main/java/ani/saikou/parsers/manga/QiScan.kt)
Create a new parser class `QiScan` that implements `MangaParser`.
- **Base URL:** `https://api.qimanga.com/api/v1`
- **Search:** `GET /series/search?q={query}`
- **Chapters:** `GET /series/{slug}/chapters` (handling pagination to load all chapters).
- **Images:** `GET /series/{slug}/chapters/{chapter-slug}`

#### [MODIFY] [MangaSources.kt](file:///Users/sanalreghunath/StudioProjects/Saikou/app/src/main/java/ani/saikou/parsers/MangaSources.kt)
Register the new `QiScan` source in the `MangaSources` object.

## Verification Plan

### Automated Tests
- N/A - The project does not have a testing framework for parsers.

### Manual Verification
1. Open the app and go to Manga Sources.
2. Select "QiScan".
3. Search for a manga (e.g., "Solo Farming").
4. Verify that the manga details and chapter list load correctly.
5. Open a chapter and verify that images load and are viewable.

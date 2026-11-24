# Vision IPTV V2 — Android TV source foundation

This is an incremental source-code foundation, **not a finished or production-ready app**. It has not been compiled or tested on a television in this environment.

## Included in this iteration
- Android TV launcher entry and dark Compose interface
- Basic TV-oriented navigation and Live TV list
- M3U URL loading and `#EXTINF` parsing
- Media3 / ExoPlayer playback screen
- Data models for primary + two backup URLs, accounts, channels, media and EPG programmes
- Xtream-compatible live and VOD request helpers
- XMLTV programme parser and `tvg-id`-first matching helper
- Basic network timeouts and HTTP error handling

## Remaining work for the agreed V2
- Full Netflix-style Movies/Series catalogue, seasons, episodes, artwork and detail screens
- Sky/NOW-style Live TV layout, category rail, preview, Now/Next and full EPG grid
- UI and persistence for multiple accounts and primary/backup URLs
- Automatic stream failover during playback, retry and active-server status
- XMLTV source priority/failover, cache, timezone/DST display and worldwide guide controls
- Provider-specific catch-up URL handling
- Secure credential storage, parental PIN, favourites and continue-watching
- Automated tests, D-pad focus/accessibility checks and real-device testing

## Build
Open this folder in Android Studio, use JDK 17 and Android SDK 35, sync Gradle, then build the `app` configuration. Android Studio may prompt to install SDK components.

## Security and compatibility
Only use playlists and IPTV services you are authorised to access. Provider APIs, backup-server compatibility and catch-up URL formats vary. Do not assume a backup server is interchangeable unless it belongs to the same compatible service and accepts the same credentials.

This prototype currently sends credentials as URL query/path values and enables cleartext HTTP for legacy provider endpoints. Do not use real sensitive credentials until encrypted storage and log redaction are implemented. Prefer HTTPS whenever available.


## Build an APK using GitHub Actions (no local Android Studio needed)
1. Create a new **private** GitHub repository.
2. Extract this ZIP on your laptop.
3. Upload the extracted project files and folders to the repository root. Make sure `.github/workflows/android-apk.yml` is included.
4. Commit the files to the `main` branch.
5. Open the repository's **Actions** tab and select **Build Vision IPTV APK**.
6. Choose **Run workflow** (or wait for the first push build), then open the completed run.
7. Under **Artifacts**, download `Vision-IPTV-debug-APK`. Extract it to get `app-debug.apk`.

The workflow creates a **debug APK** for installation/testing. It is not a signed release build for public distribution. GitHub's hosted runner performs the compilation; no IPTV login details are needed for the build. Do not commit real playlist URLs, usernames or passwords to the repository.


## Fire TV remote navigation update
The sidebar now uses Material buttons with visible focus feedback and requests initial focus when the app opens. Channel rows are also buttons with focus feedback. Rebuild using GitHub Actions and test with the Fire TV remote. Physical device testing is still required.


## Visual and category-settings update
- Movies and Series now have a cinematic streaming-home layout inspired by the user's reference: large featured hero, horizontal discovery rows, and focus-highlighted cards.
- Settings now includes persistent switches to show/hide categories independently for Live TV, Movies, and Series.
- Live TV category chips filter channel groups when enabled; disabling the option shows the full channel list.
- Movies/Series currently show presentation scaffolding; real provider catalogue, artwork, categories, details and episodes still need to be connected. Hero and poster artwork in this iteration are visual placeholders, not provider artwork.


## Country subcategory visibility controls
The three independent Settings switches are specifically labelled for country subcategories in Live TV, Movies and Series. They are persisted locally. The current prototype's M3U Live TV list uses provider group names as its available grouping data; country detection/mapping and real VOD/series category integration remain provider-dependent and need to be connected when the catalogue screens are implemented.


## M3U and Xtream account setup
Settings includes M3U and Xtream Codes modes. M3U accepts a primary playlist URL, two backup M3U URLs and optional username/password. Xtream accepts username/password, one primary server URL and four backup server URLs. The connect action tries configured endpoints in order and reports which source returned the live channel list. This is connection/catalogue-load failover, not automatic failover during an already-playing stream. Credentials are held in app memory only and are not persisted; secure encrypted account storage remains future work. Use only backup endpoints from the same compatible provider/service.


## Left navigation and Live TV favourites update
- Reworked the left navigation into large rounded-rectangle focus/selection tiles. Focus uses a bright teal fill and pale outline; the selected destination remains visibly highlighted when focus moves away.
- Added a star toggle beside every Live TV channel. The star is D-pad focusable and can add/remove a channel from favourites.
- Added a Favourite Channels section above All Channels in Live TV.
- Favourite channel IDs are stored in SharedPreferences and persist across app restarts on the same device. The current storage is local to this installation; it does not sync across devices or separate playlists.
- Fire TV physical remote testing is still required.


## Reference-screen visual implementation
The Compose UI has been revised toward the supplied nine-panel Vision IPTV reference: persistent left rail, teal rounded selection, cinematic home/movie/series hero and horizontal shelves, Live TV favourites/all-channel split with a preview panel, EPG-style grid, catch-up shelves, My List favourites, and connection/category settings. Catalogue poster tiles and EPG programme cells are presentation placeholders until real provider VOD/series and XMLTV data are wired into these screens. Existing M3U/Xtream connection code and local channel favourites remain the data foundation. No APK was compiled or device-tested as part of this source update.


## EPG fallback and channel-logo enrichment
- Settings now accepts a primary alternative XMLTV/EPG URL and a backup XMLTV/EPG URL. Values are saved in app preferences.
- On Xtream connection, the app tries the account's `xmltv.php` feed first, then the configured alternative XMLTV URL, then the backup URL.
- Failed/unreachable XMLTV sources are skipped rather than interrupting the live playlist connection.
- XMLTV channel IDs and display names are matched against playlist `tvg-id`/channel names. The first matching external icon URL fills a blank playlist channel logo; existing provider logos are retained.
- Programme entries from successful feeds are merged and mapped to the app's channel IDs/names. The TV Guide displays matched Now/Next programme titles instead of fixed demo labels.
- For M3U playlists, use the Settings alternative and backup XMLTV URLs where the playlist's `url-tvg` header is absent or unusable. Automatic extraction of an embedded M3U `url-tvg` header is not part of this update.
- XMLTV feeds vary in channel naming, time coverage, size and access requirements. Only channels with a matching XMLTV ID or display name can be enriched; no external logo is invented. The feed must be reachable from the device and authorized for use.
- No APK build or Fire TV device test was run for this source update.


# Vision IPTV 2.1.0 — EPG cache build

## What changed in this build
- EPG XMLTV feeds are cached locally as compressed files under the app's private `files/epg-cache` directory.
- Each configured EPG URL has its own cache file, identified by a SHA-256 hash of that URL.
- When a feed responds successfully with channel/programme data, its latest XML is cached.
- If that same feed later fails or is unreachable, Vision IPTV reads the last successful cached copy and parses it.
- Cache write/read failures are non-fatal and do not prevent live channel loading.
- Existing provider logos are kept. Blank logos can be filled from matching XMLTV `<icon src>` metadata.
- EPG programme data from multiple available/cached feeds is merged by channel and programme start/title.
- App version is incremented to versionCode 3 / versionName 2.1.0. Settings and the left brand rail show the build number.

## Important: install the complete source update
This archive is a complete project snapshot, not just a patch. To avoid missing one of the discussed changes, replace the corresponding files/folders in your GitHub repository with **all** files from this ZIP, preserving the paths. In particular ensure `MainActivity.kt`, `IptvRepository.kt`, `EpgXmltvParser.kt`, `Models.kt`, `app/build.gradle.kts`, and the workflow file are updated. Commit the changes to the `main` branch because the included workflow triggers on pushes to `main`.

After the build completes, download the artifact named `Vision-IPTV-debug-APK`, extract the APK, install it, and open Settings. Confirm the app shows **VERSION 2.1.0** / **EPG CACHE ENABLED**. If it still shows the previous version, the new APK was not installed or the workflow built a different branch/commit.

## Limitations
- The cache is local to this app installation and is removed if the app is uninstalled or its data is cleared.
- It can only replay feeds previously downloaded successfully from the same configured URL.
- It cannot create missing programme data for channels never present in the cached feed. External XMLTV feeds still need channel IDs/display names that can be matched to the playlist.
- The EPG refresh is initiated when connecting/loading EPG or by using Test & Load EPG Sources; background scheduled refresh is not included.
- This source project has not been compiled or device-tested in this environment. Build with the included GitHub Actions workflow and verify before using it.

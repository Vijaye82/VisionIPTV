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

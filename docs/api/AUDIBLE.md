# Audible Integration (`:api-audible` & `io.github.thake.soundnest.feature.spokenword.audible`)

SoundNest supports **Audible** as an optional second audiobook source alongside Spotify, allowing a parent to connect their own Audible account and cherry-pick individual owned audiobooks for their child without exposing the rest of the parent's Audible library, Audible Store, or Audible Plus catalog.

## 1. Security, Privacy & Legal Boundaries

SoundNest enforces strict boundaries across authentication, catalog access, DRM playback, and parental controls:

1. **Standard Widevine CDM Streaming Only (Zero DRM Circumvention)**
   - SoundNest requests `drm_type = "Widevine"` and `consumption_type = "Streaming"` (`DASH` / `HLS`) from Audible's playback license endpoint and hands encrypted media segments directly to Android's platform `MediaDrm` (`C.WIDEVINE_UUID`) via Jetpack Media3 (`ExoPlayer`).
   - SoundNest **never** requests `Adrm` (`.aax` / `.aaxc`), never extracts or computes activation bytes, and never writes decrypted audio frames to disk.
2. **Ephemeral Login & Zero Persistent Amazon Web Cookies**
   - Sign-in runs inside a dedicated isolated AndroidX WebKit `Profile` (`audible_ephemeral_login`) that restricts navigation strictly to Amazon/Audible authentication hosts.
   - As soon as Amazon redirects to `/ap/maplanding` with `openid.oa2.authorization_code`, the WebView stops loading and wipes all cookies, web storage, and HTTP caches in `audible_ephemeral_login`.
   - Virtual device registration (`POST /auth/register`) requests **only** `["bearer", "mac_dms"]` — never `"website_cookies"` — so the device holds no web shopping session.
3. **Hardware-Backed Credential Storage**
   - Refresh tokens, short-lived access tokens, the 2048-bit RSA device private key, and the `adp_token` are serialized into `AudibleCredentialStore` and encrypted at rest using an Android Keystore `AES/GCM/NoPadding` master key (`soundnest_audible_master_v1`), stored inside `Context.noBackupFilesDir` (`audible_credentials.enc` with `allowBackup` excluded).
   - Secrets are never included in `soundnest-policy.json` backups or diagnostic log exports.
4. **Fail-Closed Parental Gating & Zero Store/Plus Discovery**
   - SoundNest calls only `GET /1.0/library` (owned library sync), `GET /1.0/content/{asin}/metadata` (per-book chapter metadata), `POST /1.0/content/{asin}/licenserequest` (streaming manifest + service certificate), and `POST /1.0/content/{asin}/drmlicense` (Widevine challenge exchange). Store search, Plus catalog browsing, wishlists, and 1-Click purchasing endpoints are completely absent.
   - Every Audible book is **blocked by default** in **both** `FILTERED_OPEN` and `STRICT_ALLOWLIST` modes until a parent enables `spokenWord.allowAudible` and explicitly approves its ASIN in `ParentalPolicy.spokenWord.audible.allowedAsins`.
5. **Local-Only Playback Progress**
   - Listening positions and finished-chapter marks are saved strictly on-device in `AudibleProgressStore` (`filesDir/audible/progress.json`). SoundNest never writes positions back to Amazon WhisperSync, keeping the parent's own bookmarks untouched.

---

## 2. Module Split

| Layer | Location | Responsibility |
| :--- | :--- | :--- |
| **Pure-JVM Protocol Client** | `:api-audible` (`base/api-audible`) | Marketplace registry, OAuth 2.0 + PKCE (`S256`) URL builder, virtual device registration/refresh/deregistration, `SHA256withRSA:1.0` ADP request signing, `GET /1.0/library` & `GET /1.0/content/{asin}/metadata` parsers, and Widevine `licenserequest` manifest / `drmlicense` challenge exchange. |
| **Canonical URIs** | `:api` (`MediaKind`) & `:app` (`AudibleUris`) | Recognizes `audible:<asin>`, `audible:book:<asin>`, and `audible:chapter:<asin>:<index>` across `MediaKind.of`, `SongPlayer`, `Api`, and `ParentalFilterEngine`. |
| **On-Device Stores** | `io.github.thake.soundnest.feature.spokenword.audible.data` | `AudibleCredentialStore` (Keystore AES-GCM), `AudibleCatalogCache` (synced library metadata + cached chapter tables), `AudibleProgressStore` (per-ASIN local resume & finished chapters). |
| **Native Widevine Player** | `io.github.thake.soundnest.feature.spokenword.audible.playback` | `AudiblePlayer`, `AudibleMediaDrmCallback`, `AudibleChapterProjector`: Media3 `ExoPlayer` (`DashMediaSource` / `HlsMediaSource` + `DefaultDrmSessionManager`), single-stream-to-chapter projection, mutual exclusion with Spotify `SongPlayer` and `LocalPlayer`, and instant policy-revocation stop. |
| **Parent & Kid UI** | `io.github.thake.soundnest.feature.spokenword.audible.{settings,ui}` | `AudibleParentSettingsUi`, `AudibleLoginDialog`, `AudiblePickerSheet`, `AudibleShowPageLoader`: Marketplace auto-default + manual override sign-in flow, Parent Picker sheet with independent **Approve** and **Pin to Home** controls, and Classic + Immersive Library, Home, Search, ShowScreen, and PodcastPlayer integration. |

---

## 3. Marketplaces & Auto-Defaulting

`AudibleMarketplace` maps all 11 regional Audible/Amazon domains:

| Code | Domain | Amazon Auth Host | Audible API Host | Marketplace ID |
| :--- | :--- | :--- | :--- | :--- |
| `DE` | `audible.de` | `www.amazon.de` | `api.audible.de` | `AN7V1F1VY261K` |
| `US` | `audible.com` | `www.amazon.com` | `api.audible.com` | `AF2M0KC94RCEA` |
| `UK` | `audible.co.uk` | `www.amazon.co.uk` | `api.audible.co.uk` | `A2I9A3Q2GNFNGQ` |
| `FR` | `audible.fr` | `www.amazon.fr` | `api.audible.fr` | `A2728XDNODOQ8T` |
| `CA` | `audible.ca` | `www.amazon.ca` | `api.audible.ca` | `A2CQZ5RBY40XE` |
| `AU` | `audible.com.au` | `www.amazon.com.au` | `api.audible.com.au` | `AN7EY7DTAW63G` |
| `IT` | `audible.it` | `www.amazon.it` | `api.audible.it` | `A2N7FU2W2BU2ZC` |
| `ES` | `audible.es` | `www.amazon.es` | `api.audible.es` | `ALMIKO4SZCSAR` |
| `JP` | `audible.co.jp` | `www.amazon.co.jp` | `api.audible.co.jp` | `A1QAP3MOU4173J` |
| `IN` | `audible.in` | `www.amazon.in` | `api.audible.in` | `AJO3FBRUE6J4S` |
| `BR` | `audible.com.br` | `www.amazon.com.br` | `api.audible.com.br` | `A10J1VAYUDTYRN` |

`AudibleMarketplace.fromLocale(Locale.getDefault())` pre-selects the matching regional marketplace (falling back to `US`), and the parent can manually override the marketplace in `ParentSettingsScreen` before tapping **Connect Audible Account**.

---

## 4. Authentication & ADP Request Signing

### 4.1 OAuth 2.0 + PKCE Virtual Device Registration (`AudibleAuth`)
1. Generate a 40-character random uppercase alphanumeric device serial and build `client_id = hexEncode("$serial#A2CZJZGLK2JJVM")`.
2. Generate a RFC 7636 PKCE `code_verifier` and `S256` `code_challenge`.
3. Open `https://www.{amazonDomain}/ap/signin` with OpenID + OAuth2 PKCE parameters redirecting to `https://www.{amazonDomain}/ap/maplanding`.
4. Exchange `openid.oa2.authorization_code` at `POST https://api.{amazonDomain}/auth/register` with `"requested_token_type": ["bearer", "mac_dms"]` (never `"website_cookies"`).
5. Persist the returned `adp_token`, `device_private_key` (2048-bit RSA PEM), `refresh_token`, and `access_token`.
6. Refresh expired bearer tokens via `POST https://api.{amazonDomain}/auth/token` and deregister on disconnect via `POST https://api.{amazonDomain}/auth/deregister`.

### 4.2 `x-adp-signature` (`AdpRequestSigner`)
Every call to `https://api.{audibleDomain}/1.0/...` is signed with the registered RSA private key:

```text
{HTTP_METHOD}\n{PATH_AND_QUERY}\n{UTC_ISO8601_TIMESTAMP}\n{REQUEST_BODY}\n{ADP_TOKEN}
```

Headers attached to signed requests:
- `x-adp-token: {adp_token}`
- `x-adp-alg: SHA256withRSA:1.0`
- `x-adp-signature: {Base64(SHA256withRSA(canonicalPayload))}:{UTC_ISO8601_TIMESTAMP}`
- `Authorization: Bearer {access_token}`

---

## 5. Catalog Sync, Widevine Playback & Parental Controls

### 5.1 Library Sync (`GET /1.0/library`)
`AudibleApiClient.fetchAllLibraryBooks()` pages through `GET /1.0/library` (`num_results=500`, `response_groups=contributors,media,product_attrs,product_desc,product_extended_attrs,series`), filtering out non-playable entries without runtime metadata and caching normalized `AudibleBook` records in `AudibleCatalogCache`.

### 5.2 Chapter Metadata, Widevine Manifest & License Flow (`GET /1.0/content/{asin}/metadata`, `POST /1.0/content/{asin}/licenserequest` & `POST /1.0/content/{asin}/drmlicense`)
1. **Chapter Table & Manifest**: `AudibleApiClient.fetchChapters(asin)` fetches the flat chapter table via `GET /1.0/content/{asin}/metadata?response_groups=chapter_info,content_reference&chapter_titles_type=Flat&quality=High` (with recursive flattening of nested multi-part `chapters` trees) and caches it in `AudibleCatalogCache`. When playback starts, `AudibleApiClient.requestStreamManifest(asin)` calls `POST /1.0/content/{asin}/licenserequest` (`drm_types = ["Widevine", "Dash", "HlsCmaf"]`, `consumption_type = "Streaming"`, `quality = "High"`, `chapter_titles_type = "Flat"`), extracting the signed `DASH` / `HLS` manifest URL (`content_license.license_response` or `content_metadata.content_url`) and optional Widevine service certificate (`content_license.certificate`).
2. **Widevine License Exchange**: When ExoPlayer's `DefaultDrmSessionManager` emits a `ProvisionRequest` or `KeyRequest`, `AudibleMediaDrmCallback` Base64-encodes the raw Widevine CDM challenge into `licenseChallenge` on `POST /1.0/content/{asin}/drmlicense` (`consumption_type = "Streaming"`, `drm_type = "Widevine"`, `tenant_id = "Audible"`) and passes the returned `license` bytes directly into Android's `MediaDrm` session in memory.
3. **Single-Stream Chapter Projection**: Because Audible delivers an audiobook as a continuous MPEG-DASH / HLS timeline with byte/millisecond chapter markers, `AudibleChapterProjector` maps continuous player positions onto `audible:chapter:<asin>:<chapterIndex>` rows so `SongPlayer`, `PodcastPlayerScreen`, `ImmersivePodcastPlayer`, `ShowScreen`, `ImmersiveShowScreen`, and Sleep Timer ("End of chapter") operate seamlessly per chapter.

### 5.3 Separate Library Approval vs. Home Pinning & Independent Toggles
- `ParentalPolicy.spokenWord.allowAudible: Boolean` (default `false`) — independent master toggle for Audible audiobooks, completely separated from `ParentalPolicy.spokenWord.allowAudiobooks` (Spotify audiobooks). Allows parent-approved Audible playback and library surfaces without exposing Spotify's separately-billed audiobook catalog.
- `ParentalPolicy.spokenWord.audible.allowedAsins: Set<String>` — books approved in the **Parent Picker** (`AudiblePickerSheet`). Surfaces approved books under Library (`Audiobooks` chip), local Search, and `ShowScreen` / `ImmersiveShowScreen`.
- `ParentalPolicy.spokenWord.audible.pinnedAsins: Set<String>` — strict subset of `allowedAsins` (revoking approval automatically unpins the book).
- `ParentalPolicy.home.showPinnedAudibleOnHome: Boolean` (default `true`) — dedicated shelf toggle controlling whether books in `pinnedAsins` appear in the **Parent Picks** shelf on Home.

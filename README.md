<div align="center">
  <img src="docs/logo.png" width="160" alt="DiscordRP logo">
</div>

<div align="center">
  <h1>DiscordRP Music</h1>
  <h4>Music Rich Presence for Android</h4>
</div>

<p align="center">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android%208.0%2B-FF7EB6">
  <img alt="Google Play Services" src="https://img.shields.io/badge/Google%20Play%20Services-not%20required-A97BFF">
  <img alt="License" src="https://img.shields.io/badge/license-GPL--3.0-blue">
</p>

## About

DiscordRP Music reads Android media sessions and shows the track you're listening to as a Discord activity.

It connects to Discord directly. No desktop companion or Google Play Services are required. Message notifications are not part of this app.

## Features

- Track title, artist, album, playback state and timeline
- Artwork from Android media metadata and direct HTTPS image links
- Yandex Music, YAYAMA, Spotify, Deezer, Tidal, SoundCloud and local media players
- Optional MusicBrainz cover lookup when the player has no usable artwork
- Optional custom image used as the cover for every track
- Optional hosting for bitmap-only player artwork, disabled by default and limited to unmetered networks
- Discord status and mobile badge controls
- Automatic reconnects and optional start on boot
- Bundled default artwork when no cover or app icon is available

## Requirements

- Android 8.0 or newer
- A Discord account
- An internet connection

## Setup

1. Install the APK from [Releases](../../releases).
2. Grant **Notification access** to DiscordRP in Android settings. Android uses this special access to expose active media sessions. DiscordRP reads playback metadata, not notification text.
3. Sign in with Discord.
4. Open the Presence tab, select your players and enable sharing.
5. For reliable background playback, allow unrestricted battery use for DiscordRP.

## Artwork

DiscordRP prefers artwork provided by the player. Public HTTPS artwork URLs are sent directly as Rich Presence assets.

Some players provide only a local bitmap. The optional **Host bitmap-only covers on Wi-Fi** setting can compress and upload those images to Catbox. It is off by default. Uploaded images have public links.

You can also choose one custom image for all tracks. Android images are uploaded only after you confirm the public upload notice. Anyone with the resulting link can view the image.

If no artwork is available, DiscordRP uses its bundled default image.

## Network and privacy

- DiscordRP does not read message or notification text.
- There is no message mirroring or message notification feature.
- Your Discord login is stored on the device using Android Keystore protection.
- Player artwork supplied as a public HTTPS URL is not re-uploaded by DiscordRP.
- Bitmap hosting and custom covers use Catbox and make the uploaded image public.
- MusicBrainz lookup is optional and is not used on metered mobile connections.

## Updating

Install newer builds from [Releases](../../releases). New releases use the same signing key so they can update an existing installation.

A one-time reinstall may be required when moving from an older build signed with a different key. Android will report a signature conflict if the signing keys do not match. Uninstalling removes the app's local settings and login.

## Credits

The Rich Presence implementation draws on ideas from [Kizzy](https://github.com/dead8309/Kizzy) by **dead8309**.

Security hardening of token storage, the login page and updates by **cutieway** in [#31](../../pull/31).

## License

[GPL-3.0](License)

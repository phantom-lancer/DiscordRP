<div align="center">
  <img src="docs/logo.png" width="160" alt="DiscordRP logo">
</div>

<div align="center">
  <h1>DiscordRP</h1>
  <h4>Restore Push / Rich Presence</h4>
</div>

<p align="center">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android%208.0%2B-FF7EB6">
  <img alt="No Play Services" src="https://img.shields.io/badge/Play%20Services-not%20required-A97BFF">
  <img alt="License" src="https://img.shields.io/badge/license-GPL--3.0-blue">
</p>

## About

DiscordRP is built for de-Googled Android in mind: GrapheneOS, CalyxOS, LineageOS and /e/OS.

DiscordRP keeps its own connection to Discord and shares the music you play on your profile.
It does not create message notifications.

## Features

- [x] Music rich presence from supported Android media sessions
- [x] Album art from the player's own media metadata, MusicBrainz, or optional Deezer backup
- [x] Set your Discord status and mobile badge from your phone
- [x] Fast play, pause, stop, and seek updates
- [x] Starts on boot and reconnects automatically

## Screenshots

<div align="center">
  <img src="docs/screens/home.png" width="30%" alt="Home: connection state and now playing">
  <img src="docs/screens/presence.png" width="30%" alt="Presence: status and card contents">
</div>

## Download

Get the latest APK from [Releases](../../releases). Android 8.0 or newer, no Google account
or Play Services required.

> **Warning**
> This app uses the Discord gateway connection. Use it at your own risk.

## Setup

1. Install and open the app
2. Grant notification access so DiscordRP can read active media sessions
3. Sign in with Discord
4. Turn the connection on

Enable **Unrestricted battery** during setup. Aggressive battery management can suspend the connection while the screen is off.

## Privacy

No analytics, no crash reporting, and no server in the middle. The app talks to Discord,
MusicBrainz, the Cover Art Archive, and (when enabled) Deezer directly. Your login stays on the device and is
removed by signing out.

Album art is taken from the player's HTTPS media metadata first. Yandex Music image-size placeholders
are normalized automatically. MusicBrainz and the Cover Art Archive are used as fallbacks. In the Presence tab, under
**Album covers**, you can pick **MusicBrainz (+ Deezer backup)**. The app then asks Deezer
directly, and only when MusicBrainz has no cover.

## Help [MusicBrainz](https://musicbrainz.org)

With **MusicBrainz (+ Deezer backup)** on, DiscordRP shows a button on Home when
MusicBrainz is missing the album/cover. Tap it to add the album (filled in from Deezer
with its cover) or just the cover (from your player), so the cover shows for everyone and
loads faster from then on.

## Credits

The rich presence implementation draws on ideas from [Kizzy](https://github.com/dead8309/Kizzy)
by **dead8309**.

Security hardening of token storage, the login page and updates by **cutieway** in
[#31](../../pull/31).

## License

[GPL-3.0](License)

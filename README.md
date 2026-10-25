# Open TV Launcher

A home screen for Android TV and Google TV, built on your Trakt lists and Stremio. It runs on the TV, with no server.

![Home](docs/images/home.png)

![Details](docs/images/details.png)

![All Apps](docs/images/all-apps.png)

## Requirements

> [!IMPORTANT]
> **Minimum:** Stremio installed on the TV, and a Trakt account with your own Trakt API app. Both are free. Without them, the launcher is not useful.
>
> You also need:
>
> - An Android TV or Google TV device with Android 11 or newer.
> - A computer on the same network as the TV.
> - adb, with Wireless debugging or USB debugging turned on in the TV's developer options.

## Give this prompt to your AI

Paste this prompt into an AI coding assistant on your computer, such as Claude Code or Codex. It sets up the launcher with you, step by step, and asks before it changes anything.

```text
Help me install Open TV Launcher on my Android TV or Google TV.
The repository is https://github.com/AndrewBeniston/open-tv-launcher.
Read docs/install.md first. It has the details, and the scripts folder
does most of the work. Adapt each step to my computer and my TV. Tell me
what you will do before you do it.

Goals, in order:
1. Check the minimum with me: Stremio is installed on the TV, and I have
   a Trakt account. If either is missing, stop and tell me what to do.
2. Find out my operating system. Check for a JDK 17 or newer, the Android
   SDK (command-line tools or Android Studio) and adb. Ask me before you
   install anything.
3. Clone the repository.
4. Help me make a free Trakt API app in my browser on trakt.tv: any name,
   and the redirect URI urn:ietf:wg:oauth:2.0:oob. Then write
   local.properties with sdk.dir, TRAKT_CLIENT_ID, TRAKT_CLIENT_SECRET,
   APPLICATION_ID (keep the default, app.opentvlauncher, unless I want
   another) and WIKIDATA_CONTACT (optional). Never commit local.properties,
   and never print the client secret.
5. Make a signing key with scripts/make-keystore.sh, and build the release
   APK. Tell me to back up the signing folder.
6. Connect to the TV with adb. Help me turn on the developer options and
   Wireless debugging or USB debugging. Explain that Google TV needs
   pairing first, that the pairing port and the connect port differ and
   can change, and that Wireless debugging turns off when the TV restarts.
7. Before you change any TV setting, run scripts/tv-settings.sh backup,
   and keep the file.
8. Install the APK, grant READ_TV_LISTINGS, and walk me through the Trakt
   sign-in on the TV.
9. Offer the optional steps one at a time, and wait for my OK: the
   screensaver, then the Home override (docs/home-override.md). Say what
   each one does and how to undo it.
10. Check that the launcher opens, the rows load, and the Home key works as
    docs/home-override.md says. Then tell me what you did, and how to undo
    each part. If something fails, read docs/troubleshooting.md.

Safety rules:
- Never disable, uninstall or clear the data of the TV's own launcher or
  setup apps. On Google TV, never disable
  com.google.android.apps.tv.launcherx or
  com.google.android.tungsten.setupwraith: the TV can be left with a
  black screen.
- Ask me before every change on the TV.
- Keep the undo commands ready: scripts/home-override.sh off,
  scripts/tv-settings.sh restore, and adb uninstall.
- Never push, publish or share my local.properties, my keys or my signing
  folder.
```

## Manual install and more

- [Install by hand](docs/install.md): every step, how to undo it, and the limits.
- [The Home override](docs/home-override.md): what it does, how to turn it on and off.
- [Features](docs/features.md): the rows, the menus, Settings, All Apps and the screensaver.
- [Troubleshooting](docs/troubleshooting.md)
- [Development](docs/development.md): tests and versions.

## Licence

MIT. See [LICENSE](LICENSE). The Autospawn Sans fonts are under the SIL Open Font License, in `app/src/main/assets/fonts/OFL.txt`. App names and marks belong to their owners.

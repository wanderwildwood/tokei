# Clock — Privacy Policy

Version 0.2.3. Last reviewed 23 October 2026.

## The short version

Clock collects nothing and sends nothing anywhere. It has no internet permission, so no data can leave your phone through it. There is no account, no sign-up, no analytics, no advertising and no crash reporting. The developer never receives anything about you or your use of the app, and has no way to.

Two other apps of the same author can exchange a line with it on your phone, but only when you switch them on, and only those two (see "Who else receives anything").

## Who is responsible

wander wildwood, the author of the app. Contact: email@wanderthe.dev

Because no personal data is transmitted to or held by the developer, there is no data controller relationship for the developer to administer. Where the app processes personal data, that processing happens on your own device, under your control.

## What the app stores, and where

In its own private storage on the phone: your alarms (time, days, name, sound, vibration, on or off, snooze state), your timers, a list of alarms and timers that rang unanswered, the stopwatch, the cities on your world clock, and your settings (snooze length, how long an alarm rings, and the switches described below). If you choose a sound from your files, the app keeps the address of that one file and the permission Android gives it to read that file; it reads no other file.

The app is left out of Android's backups (`allowBackup="false"`), and uninstalling it removes all of it. There is no cloud copy and no copy held by the developer.

## Permissions, and what each is for

`app/src/main/AndroidManifest.xml` declares these, and each is about ringing on time:

- `SCHEDULE_EXACT_ALARM`: to ring at the minute an alarm is set for.
- `RECEIVE_BOOT_COMPLETED`: Android forgets alarms at a restart; this is how they are set again. The app also listens for the time, time zone and language changing, and for its own update, to set them again.
- `USE_FULL_SCREEN_INTENT`: to show the ringing screen over the lock screen.
- `FOREGROUND_SERVICE`: for the service that plays the sound until the alarm is answered.
- `VIBRATE`: for alarms set to vibrate.
- `WAKE_LOCK`: to keep the phone awake while it rings.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: so the first screen can ask for the phone's battery saving to leave the app alone. It is asked, never assumed.
- `POST_NOTIFICATIONS`: for the notification that carries Snooze and Stop (Android 13 and later asks for this).

There is no internet, location, contacts, storage, microphone or camera permission. The manifest also names two other apps it may look for, Sky (`com.wanderwildwood.soramoyo`) and DuraSpeed (`com.mediatek.duraspeed`, the Kompakt's background manager, only to say on the first screen whether it is there and whether it is switched on), and lists the apps that offer a text action, so the selection menu can show them.

The store also lists `com.wanderwildwood.tokei.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` because it reads the APK. AndroidX declares it for its own internal receivers; it is signature-level and namespaced to this app, so nothing else can hold it and it grants no access to anything on the phone.

## Who else receives anything

No server. The app contacts no host.

Other apps on the same phone:

- **Glance** (`com.wanderwildwood.hitome`), only if you switch on Settings, "Next alarm on the lock screen". Clock then answers Glance, and no other app, with the time and name of your next alarm and any running timer, for Glance to draw on the lock screen. While the switch is off it answers with nothing.
- **Sky** (`com.wanderwildwood.soramoyo`), only if you switch on Settings, "Weather when an alarm rings". When an alarm rings, Clock asks Sky for its current line of weather and shows it under the alarm. Clock sends Sky nothing about you or the alarm. Sky fetches the forecast itself, and its own privacy policy covers that.
- Alarms, timers and the stopwatch can be set, snoozed or stopped by other apps that hold Android's `SET_ALARM` permission, as with Android's own clock. They can ask the app to show alarms, timers and the clock; nothing is changed by showing.

In About, "feed the llamas" opens a donation page (square.link) and wanderthe.dev opens the author's website, in your browser and only when you press them. The app does not fetch either page and learns nothing about whether you went.

No data is sold, rented, or shared. There is no profiling, no personalization, and no automated decision-making about you.

## Legal basis (EU / EEA and UK)

No personal data is collected, transmitted or processed by the developer, so no legal basis under Article 6 GDPR is engaged on the developer's part. Processing that happens wholly on your own device, for your own purposes, is outside the scope of the Regulation (Article 2(2)(c)).

## Your rights

Under the GDPR and UK GDPR you have rights of access, rectification, erasure, restriction, portability and objection. Under the CCPA/CPRA and comparable North American laws you have rights to know, delete, correct and opt out of sale or sharing.

The developer holds no data about you, so there is nothing to request from them, and nothing has ever been sold or shared. You can exercise every one of these rights directly: the data is on your phone. Delete an alarm in the app, clear the app's data in Android's settings, or uninstall to remove all of it at once.

## Children

The app is suitable for all ages and does not collect anything from anyone, of any age.

## Changes

If a later version changes what the app stores or starts contacting anything, this policy is updated in the same release and the change is described in the release notes. The source is public, so any claim here can be checked against the code: <https://github.com/wanderwildwood/tokei>. It is released under GPL-3.0-only.

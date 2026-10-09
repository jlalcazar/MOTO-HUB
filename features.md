# MOTO-HUB Features

Current version: `1.1.120 (214)`, Android 12+.

This document describes implemented functionality of MOTO-HUB CORE, the open-source app in this
repository. It must be updated whenever a feature is added, removed, renamed, or materially
changed.

The Ride Dashboard, navigation, trip recording, and OBD engine data are not part of this app. They
live in [MOTO-HUB ADV-SOLO](https://github.com/vincenzobpt/MOTO-HUB-ADV-SOLO-releases), a separate
standalone app. CORE has no GPS features and requests no location updates.

## Motorcycle Pairing And Connection

- Pair with a motorcycle dashboard by scanning its QR code.
- Import and decode a QR code from an existing photo.
- Pair manually by entering the network name and password.
- Read the QR dialects used by different manufacturers, including CFMOTO, MotoFun (Moto Morini),
  and YUNMO.
- Accept an unrecognized QR code after a warning instead of rejecting it.
- Store Wi-Fi credentials securely using Android Keystore.
- Connect through one of four link types, selected automatically or forced per motorcycle:
  - the dashboard's Wi-Fi access point;
  - Wi-Fi Direct, for dashboards that advertise a `DIRECT-` network;
  - a phone hotspot the dashboard joins, using credentials the dashboard prints;
  - a phone-hosted network negotiated over Bluetooth, for dashboards that offer no other way on.
- Speak three dashboard protocols behind one transport interface:
  - EasyConn, through the `ridedaemon` library (CFMOTO-family T-Box);
  - Yunmo, with still-image or H.264 delivery (Moto Morini X-Cape 1200, KOVE 625X);
  - ThinkerRide, provisioned over Bluetooth LE (KOVE-family dashboards).
- Fall back to a generic profile for a dashboard MOTO-HUB has never seen.
- Try alternative dashboard profiles and wire formats, asking the rider whether the picture
  actually appeared, and restore the previous profile when a trial is abandoned.
- Detect an OEM companion app that is holding the EasyConn ports before a projection starts, and
  offer to stop it.
- Keep T-Box traffic bound to motorcycle Wi-Fi while preserving cellular Internet access where
  supported by Android and the phone manufacturer.
- Optionally connect to the saved motorcycle when MOTO-HUB launches.
- Optionally stay linked to the motorcycle Wi-Fi after disconnecting.

## Motorcycle Garage

- Store and manage multiple motorcycles.
- Select the active motorcycle.
- Assign a custom display name.
- Take or select a motorcycle photo.
- Display the motorcycle photo throughout the application.
- Edit or delete saved motorcycle profiles.
- Store Android Auto display preferences separately for each motorcycle.
- Store per-motorcycle TFT safe margins for displays where motorcycle UI occupies part of the
  physical panel.
- Store handlebar calibration and button mapping separately for each motorcycle.
- Inspect hardware and software information actually reported by the T-Box, including:
  - EasyConn endpoint and discovery information.
  - TFT resolution and orientation.
  - Reported DPI and screen type.
  - Head-unit, vehicle brand, and vehicle model identifiers.
  - PXC, SDK, software, and protocol versions.
  - Transport and product types.
  - Supported T-Box feature flags.
- Avoid guessing the motorcycle model from its QR code or SSID.

## Screen Mirroring

- Mirror the entire Android display to the motorcycle TFT.
- Project a single application selected through Android's system picker.
- Hardware H.264 encoding optimized for the T-Box protocol.
- Persistent foreground notification with session controls.
- Dim or obscure the phone display while mirroring continues.
- Restore the phone display using the notification or phone interaction.
- Stable stop and cleanup of the projection session.

## Android Auto

- Run Android Auto directly on the motorcycle TFT through an embedded local head-unit receiver.
- Start Android Auto on versions that no longer accept a direct start request, by connecting to
  Android Auto's own head unit server, with in-app instructions for enabling it.
- Decode, composite, re-encode, and stream Android Auto video to the T-Box.
- Display Android Auto simultaneously on the TFT and phone.
- Run Android Auto on the phone alone, with no motorcycle connected.
- Use the phone preview as a touchscreen controller for a non-touch motorcycle TFT.
- Carry the rider's voice to the Android Auto assistant through the phone microphone.
- Select a per-motorcycle TFT display mode:
  - `FIT`: preserve the complete image with black bars when required.
  - `STRETCH`: stretch the active Android Auto content to use the complete available TFT area.
  - `CROP`: fill the complete available TFT area without stretching and crop edges when required.
- Lay Android Auto out at the dashboard's real shape so the map fills the panel, or advertise the
  per-motorcycle TFT safe margins instead by switching content insets to `Manual`.
- Select Android Auto resolution automatically from learned T-Box geometry.
- Override the Android Auto source with a manual resolution:
  - Landscape 800 x 480, 1280 x 720, 1920 x 1080, 2560 x 1440, or 3840 x 2160.
  - Portrait 720 x 1280, 1080 x 1920, 1440 x 2560, or 2160 x 3840.
  - Resolutions above HD are marked experimental and are never chosen automatically.
- Select the Android Auto interface size, from 120 dpi to 480 dpi, or keep the density that comes
  with the selected resolution.
- Automatically recover supported stalled or disconnected TFT streams.
- Keep the local Android Auto receiver active during supported TFT recovery operations.
- Optionally disable TFT touchscreen advertisement so Android Auto uses focus/handlebar behavior.
- Preserve correct touch mapping through safe margins and the selected `FIT`, `STRETCH`, or
  `CROP` compositor mode.

## USB External Display

- Stream the phone screen to a USB (AOA) accessory head unit.
- Offer the mode only while a USB accessory is attached.
- Operate independently of the T-Box, EasyConn, and `ridedaemon` path.

## Physical Motorcycle Controls

- Capture Bluetooth media-button events from supported motorcycle handlebar controls.
- Capture key presses from a Bluetooth HID-keyboard handlebar remote through an accessibility
  service.
- Learn what each motorcycle actually sends through a guided calibration, without assuming
  anything from the model name.
- Map press, double press, and hold of `Up`, `Down`, `Left`, `Right`, and `Select` separately.
- Assign each gesture to an Android Auto action: rotary forward or back, D-pad, Select, Back, Home,
  or the assistant.
- Assign a gesture to music control: play/pause, next track, previous track, volume up, or volume
  down.
- Start navigation to one of three saved destinations with a single press.
- Configure double-press and hold timing.
- Optionally show each button press and the action it ran on the dashboard picture.
- Enable or disable handlebar control capture, per motorcycle.
- Preserve normal media controls when handlebar capture is disabled.
- Reset handlebar mappings to their defaults.
- Offer only actions this app can run: the Ride Dashboard actions a companion app may have mapped
  are shown but cannot be picked here.

## Video Quality

- Apply H.264 quality settings to mirroring and Android Auto.
- Select `Smoother` for reduced bitrate, heat, network load, and phone workload.
- Select `Balanced` for the recommended default quality.
- Select `Sharper` for clearer maps and text at a higher bitrate.
- Select a power mode: `Smooth` (30 FPS), `Balanced` (24 FPS), `Saver` (20 FPS), or `Auto`, which
  adapts bitrate and frame rate to phone temperature and Wi-Fi quality.
- In `Auto`, follow Android's Battery Saver: while it is on, stream at the `Saver` pace (20 FPS) with
  a reduced bitrate.

## Reliability And Recovery

- Use a persistent foreground service for projection.
- Optionally start mirroring or Android Auto automatically as soon as the motorcycle connects.
- Monitor outgoing Android Auto TFT frames with an optional recovery watchdog.
- Detect supported stream stalls, encoder failures, and T-Box network losses.
- Reacquire the network, repeat EasyConn discovery and handshake, and rebuild the encoder when
  recovery is possible.
- Optionally use seamless resume to park and resume a projection after longer T-Box interruptions.
- Hold high-performance Wi-Fi resources while streaming where Android allows it.
- Auto-reconnect to the saved motorcycle after deliberate mode stops when auto-connect is enabled.
- Perform controlled cleanup after deliberate session stops.
- Explain a session ended by the phone's battery management and point to the relevant system
  setting.

## Diagnostics

- Maintain a built-in application event log.
- Record important connection, projection, control, settings, and UI operations.
- Copy logs to the clipboard, share them, or clear them.
- Share logs as a generated diagnostic text file instead of only copying text.
- Redact IP and MAC addresses in exported logs.
- Include the app version, build number, phone model, and Android version in exported diagnostics.
- Turn all logging off with a master switch.
- Enable verbose T-Box logging for protocol-level troubleshooting.
- Show a Support ID and send a diagnostic report to support, on request or automatically.
- Report the fatal error that ended the previous run on the next launch.
- Report crashes and redacted connection errors to Sentry in official release builds; builds from
  this source send nothing.
- Run dedicated T-Box and cellular network routing tests.
- Detect likely Always-on VPN / kill-switch local-network blocking.
- Inspect detected Android networks and bound routes.
- Review projection session events.
- Display structured passed, failed, skipped, and running diagnostic results.
- Inspect T-Box capabilities captured from EasyConn `CLIENT_INFO` without displaying sensitive
  fields.
- Explore any Bluetooth LE device: scan, connect, read, write, and subscribe.
- Set the clock on dashboards that reset it at every ignition (Zontes, Voge), over Wi-Fi or,
  experimentally, over Bluetooth.

## Additional Features

- Display the application version and build number.
- Check GitHub releases and pre-releases for newer APK builds.
- Show update release notes and pre-release status before installing.
- Provide an About page with a project description, safety disclaimer, and GitHub link.
- Run in English, Italian, Portuguese, Korean, French, Spanish, German, Dutch, Czech, Turkish, or
  Russian, or follow the phone language.
- Expose the T-Box transport and the Android Auto receiver to a companion app through an AIDL
  bridge protected by a signature-level permission.
- Guide the user through connection before presenting projection modes.
- Operate locally without requiring a MOTO-HUB account.

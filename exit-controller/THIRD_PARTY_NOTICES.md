# Mullvad VPN source

This independent exit-node controller reuses source from
https://github.com/mullvad/mullvadvpn-app at revision
`a8221c1b8cb4b2e036ea25fbc58b3947009e5db6` (retrieved 2026-09-23).

The original source is retained under `vendor/mullvad/`, with its GNU GPL version 3
license. The controller's source is distributed under GPL-3.0; see LICENSE.md.
It is not an official Mullvad or Tailscale application.

Reused components:

- `android/lib/map`: OpenGL globe renderer, shaders, markers, hit testing, vectors,
  camera types and globe color types.
- `dist-assets/geo`: original globe geometry buffers.
- `android/lib/model/{LatLong,Latitude,Longitude}`: coordinate mathematics.
- `SelectableRelayListItem.kt` and `RelayListContent.kt`: source for the Android
  Views adaptation of the location picker, including separate select/expand
  targets, nested country/city/server rows, selected markers and search styling.
- `android/lib/ui/resource`: Mullvad launcher vectors, monochrome launcher
  vector, header logo and notification icon. Original files are retained in
  `vendor/mullvad/`; the adaptive launcher wrapper is specific to this app.

Modifications, 2026-09-23:

- `tools/adapt-mullvad.py` replaces Compose-only color and geometry value types
  with small local types, uses platform LruCache/Log, removes Parcelize and
  Compose annotations, and redirects resource references to this application.
  The globe shader adds directional lighting and a subtle rim tint; binary
  geometry remains upstream.
- `GlobeView.kt` adapts the interactive map to the Android View lifecycle and
  gestures, uses the controller's relay list, and animates the selected city.
  It replaces the Compose wrapper, not the underlying renderer.
- Globe colors distinguish the ocean from the background, and the camera
  distance adapts to the available viewport. The controller card uses compact
  and unfolded layouts, with horizontally swipeable, vertically scrollable pages.
  The unfolded card fits the selected page's content; a small vector target
  button recenters the globe.
- `RelayPicker.java` ports the hierarchy and row interaction to platform Views,
  removes account/daemon/multihop dependencies, and sends a validated location
  selector to the VM API.
- Application networking, pairing, DNS controls and VM integration are specific
  to this controller. No Mullvad account credentials or WireGuard keys are in
  the APK. This independent noncommercial app uses the name Mullvad Exit Node and
  upstream Mullvad logos. Trademarks remain Mullvad's property; see
  https://mullvad.net/en/help/policy-brand-rights. The source license does not
  grant trademark rights.

Kotlin standard library is included in the APK under the Apache License 2.0.
Its license is included in the APK and at `app/src/main/res/raw/kotlin_license.txt`.

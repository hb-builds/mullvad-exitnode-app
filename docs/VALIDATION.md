# Validation

## Automated checks

`tools/check.sh` covers:

- Controller operation allowlisting, malformed location selectors and DNS masks.
- Deduplicated request IDs, conflicting concurrent changes and DNS rollback/persistence.
- WireGuard config normalization, removal of executable hooks and rejection of malformed keys, endpoints, routes or missing IPv6 addresses.
- Pairing-address validation, stable token reuse and private file modes.
- Twenty Java endpoint-policy cases, including HTTPS, tailnet range, URL credentials, paths, redirects-to-be-avoided and malformed literals.
- Shell syntax. Python tests also run with `-O` to verify that input validation does not depend on assertions.

The APK build verifies its signature with apksigner. CI builds the Android app from source on Ubuntu.

## Manual reference deployment checks

On a GrapheneOS Pixel 10 Pro Fold and an Ubuntu 24.04 ARM64 gateway:

- Pairing import and signed upgrades, location switching and all three DNS filters.
- IPv4/IPv6 Mullvad egress, location-change rollback and tunnel-down forwarding/DNS blocking.
- Unfolded globe/card layout, tab and swipe navigation, content-blocking off/on restoration, recenter control and Vanadium launch.
- Passive last-checked notification and absence of an app background service.

Version 1.3 fixes hard-coded gateway addresses, stale responses/caches after re-pairing, destroyed-screen callbacks, a swipe cleanup issue, repeated picker rebuilding during status polls, and config validation relying on Python assertions.

## Limits

The generalized fresh-install procedure has not been exercised on every cloud image. The fail-closed live tests were performed on the reference gateway; CI uses isolated unit tests, not a cloud/VPN integration environment. Re-pairing rejection/state protection is reviewed in code; long-duration soak testing and an external security review have not been performed. Folded/unfolded transitions and Android versions beyond the reference device need broader community testing.

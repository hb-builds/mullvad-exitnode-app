# Security

Do not post pairing JSON, Mullvad account numbers, WireGuard configs, private keys, signing files or full VPN debug dumps in issues. Use GitHub's private vulnerability reporting for security issues.

## Boundaries

- The Android app requires an active Android VPN transport and a paired HTTPS gateway. It cannot identify which VPN owns that transport; certificate verification and the server's tailnet-only listener authenticate access.
- Pairing accepts literal `100.64.0.0/10` IPv4 endpoints. API support is IPv4-only; the gateway's internet exit supports both IPv4 and IPv6.
- The HTTPS API runs unprivileged, binds only to the gateway's Tailscale address, allows configured source IPs and checks a 256-bit bearer token. No public cloud port needs to be opened for it.
- A root Unix-socket broker validates a limited set of operations, serializes changes and deduplicates recent request IDs. It is a privileged component: review it before deployment.
- Gateway forwarding and DNS routes are blocked from falling back to the uplink. The host's own traffic remains on its normal network.
- The gateway is shared. Every allowed controller can change location/DNS for all clients. Controllers use one shared token; removing one source IP or rotating the token revokes access.
- Phone traffic outside Tailscale is outside this gateway's protection. Keep the official Tailscale app configured appropriately for your needs.

## Maintenance

Keep Ubuntu, Tailscale, WireGuard and this controller updated. Monitor Mullvad subscription/device status and Tailscale node-key expiry. Renew the controller certificate before expiry (installer default: five years), then re-pair clients.

To rotate a token, stop the API, securely replace `token` in `/etc/mullvad-controller/access.json`, regenerate pairing with the installer, and import the new JSON. Do not delete or expose the WireGuard key as part of an app-token rotation. Preserve root/group permissions documented by the installer.

The release-signing environment is restricted to `main`. Pull-request builds have no release secrets. Release APKs contain no gateway credentials; anyone can download the same APK and pair it with their own gateway.

Basic automated tests and manual device/network checks are documented in [validation notes](docs/VALIDATION.md). This is not a comprehensive security audit.

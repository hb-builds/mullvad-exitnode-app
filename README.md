# Mullvad Exit Node

An Android remote for a **Tailscale exit node that routes through your own Mullvad subscription**. Keep using the official Tailscale Android app, including its app-based split tunneling, and change the gateway's country, city, server and DNS filters from this app.

This does **not** use Tailscale's paid Mullvad integration. You supply a Linux gateway, a Mullvad subscription and a Tailscale account. Installing the APK alone does not create a gateway.

```text
Android phone → official Tailscale app → your Linux gateway → Mullvad → internet
                        ↑
               This app controls the gateway over Tailscale
```

## Get started

1. [Set up the Linux gateway](oci-exit-node/README.md). The reference deployment uses Ubuntu 24.04; the scripts are not specific to Oracle Cloud.
2. Download the signed APK from [Releases](https://github.com/hb-builds/mullvad-exitnode-app/releases/latest). Android 15 or later is required. The UI is designed for GrapheneOS and foldable phones.
3. Connect Tailscale, select your gateway as the exit node, and include this app in its VPN.
4. Import the gateway's pairing JSON. Transfer it privately and delete the transfer copies afterward.
5. Use the globe or location picker, and the Location / DNS / Tools tabs.

## Features

- Mullvad's OpenGL globe and an adapted country → city → server picker.
- Compact cards, a larger globe and a bottom-right layout when unfolded.
- Ads, trackers and malware DNS filters, individually or together.
- Refresh, pairing settings and a Mullvad connection check in Vanadium.
- Optional last-checked location notification, without background polling.
- No Mullvad account number or WireGuard private key in the Android app.

The app is a controller, not a VPN. Everyone using the same exit node shares its selected location and DNS settings. Switching briefly interrupts their internet traffic. Private DNS, browser secure DNS and apps excluded from Tailscale can bypass gateway filtering. The optional notification is a saved snapshot, not a background connection monitor.

Vanadium is required for the connection-check shortcut. Other supported Android devices can still use the controller and open `https://mullvad.net/check` themselves.

## Build, test and release

```sh
tools/check.sh
exit-controller/build.sh
```

See [build and release instructions](docs/BUILD.md). CI checks every push and pull request. The **Signed release** workflow publishes an upgrade-compatible APK and source archive using encrypted GitHub environment secrets. Pairing and VPN credentials are never needed by CI.

The same public app works for the maintainer and other users: the endpoint, certificate and token come from each user's pairing file. A separate private source repository is unnecessary.

## Security and limits

The controller API binds only to a Tailscale IPv4 address and requires both an allowed source IP and a random bearer token. The app trusts its paired TLS certificate, verifies the endpoint identity and encrypts pairing data using Android Keystore. A restricted local broker performs gateway changes. The gateway has dedicated firewall and routing rules designed to block forwarded traffic if Mullvad goes down.

See [security and operational details](SECURITY.md) and [validation notes](docs/VALIDATION.md). This is a small community project with basic tests and on-device checks, not an independently audited VPN product.

## License and attribution

GPL-3.0-only; see [LICENSE](LICENSE) and [third-party notices](exit-controller/THIRD_PARTY_NOTICES.md). Original Mullvad source and assets are retained with the adaptations. Kotlin's runtime uses Apache-2.0.

This is an independent, noncommercial project, not an official Mullvad or Tailscale application. Mullvad names and logos remain Mullvad's property; see their [brand policy](https://mullvad.net/en/help/policy-brand-rights). The software license does not grant trademark rights.

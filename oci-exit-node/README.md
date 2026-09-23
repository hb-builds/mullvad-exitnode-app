# Linux gateway setup

This gateway uses a dedicated network namespace for Tailscale and Mullvad. The host's SSH session and default route remain independent. The reference OS is Ubuntu 24.04 with systemd, native WireGuard and an IPv4 internet uplink. Both IPv4 and IPv6 addresses must be present in the Mullvad configs.

Use a VM you administer and review the scripts first. `scripts/install` enables host IPv4 forwarding and adds dedicated forwarding/NAT chains, a veth subnet (`10.203.254.0/30`), namespace routes and firewall rules. Choose another subnet in `scripts/network` first if this one conflicts. It does not configure cloud security lists or an existing host firewall for you.

## 1. Prepare the host

Install [Tailscale's Linux package](https://tailscale.com/docs/install/linux), then the other tools:

```sh
sudo apt update
sudo apt install wireguard-tools iproute2 iptables nftables curl ca-certificates dnsutils conntrack ethtool python3 openssl
```

Clone this repository and work from its root. A separate `tailscaled-mullvad-exit` service is used; an existing host Tailscale installation is not erased. Keep sufficient cloud egress capacity and check your provider's bandwidth/transfer limits.

## 2. Import Mullvad profiles

Use [Mullvad's WireGuard generator](https://mullvad.net/en/account/wireguard-config) to create **Linux configs with both IPv4 and IPv6**, selecting your desired countries/cities. Download the ZIP and privately transfer it to the VM. Note the generated device's **public** key. Do not publish the ZIP, private key or account number.

```sh
sudo bash oci-exit-node/scripts/install
sudo /usr/local/libexec/mullvad-exit-import-profiles 'YOUR_MULLVAD_DEVICE_PUBLIC_KEY' < /secure/path/profiles.zip
sudo mullvad-exit list
sudo mullvad-exit switch us-nyc
sudo systemctl enable mullvad-exit-wireguard.service
```

Choose an imported city instead of `us-nyc` if needed. The switch command starts the tunnel and checks both internet IP families. Failed verification restores the previous working config when available; otherwise the tunnel remains stopped and forwarding stays blocked. Profiles are normalized without executable hooks and stored root-only.

The installer downloads no account data. `locations.json` is a bundled public-name/availability snapshot. To refresh it before installing/updating the controller:

```sh
python3 oci-exit-node/scripts/refresh-catalog.py
```

Only profiles actually imported on the gateway appear in the app. New cities may lack map markers until the bundled app coordinate data is updated, but remain selectable in the list.

## 3. Register the exit node

```sh
sudo systemctl enable --now tailscaled-mullvad-exit.service
sudo tailscale --socket=/run/tailscale-mullvad-exit/tailscaled.sock up \
  --hostname=mullvad-exit --accept-dns=false --accept-routes=false \
  --netfilter-mode=off --advertise-exit-node
```

Complete Tailscale login and [approve the exit-node routes](https://tailscale.com/docs/features/exit-nodes) in your admin console. The netfilter-off warning is expected: this gateway supplies its own nftables policy. Keep the custom socket on all Tailscale commands for this node.

## 4. Install and pair the controller

Find the phone's Tailscale IPv4 address in the official app or admin console. Substitute it below; additional allowed controllers can be listed as further arguments.

```sh
sudo bash oci-exit-node/scripts/install-controller PHONE_TAILSCALE_IPV4
```

The installer discovers the gateway's own Tailscale IP, creates a certificate with that IP in its SAN, and generates `/etc/mullvad-controller/pairing.json` with mode 0600. Transfer that file privately to the phone, connect Tailscale, import it into this app, and remove transfer copies. Re-running the installer replaces the allowed-device list but preserves the existing token. A changed gateway address requires a matching replacement certificate and re-pairing.

The HTTPS listener is at the gateway's Tailscale IP on TCP 8443. Permit your controller devices to reach it in your tailnet policy. **Do not expose TCP 8443 on the cloud/public interface.** The API also checks its own allowed source-IP list and token. No SSH or account credentials go into the APK or GitHub.

Select this gateway as the phone's Tailscale exit node, then open `https://mullvad.net/check` in a VPN-included browser and verify the exit. Keep the controller included in Tailscale's app-based split tunneling.

## DNS and switching

```sh
sudo mullvad-exit status
sudo mullvad-exit list
sudo mullvad-exit switch gb-lon
sudo mullvad-exit dns 7
```

Country (`gb`), city (`gb-lon`) and exact server selectors are accepted. Country/city selection chooses the first active matching server by name, not the nearest/fastest one. Changes affect all clients and briefly interrupt internet traffic.

DNS bits: ads=1, trackers=2, malware=4. Mask 7 enables all three; 0 disables content blocking while retaining Mullvad DNS. The controller installer initially uses 7 unless a mask is already saved. The phone's own Private DNS/browser secure DNS can bypass these filters.

The stable resolver `10.64.0.1` is translated inside the namespace to the selected Mullvad filtering resolver. Both resolver ranges route only through WireGuard. Rules in routing table 51888 and a separate nftables forward policy block forwarded internet traffic from using the uplink when WireGuard is unavailable.

## Operations

```sh
sudo systemctl status mullvad-exit-network mullvad-exit-wireguard tailscaled-mullvad-exit mullvad-controller-broker mullvad-controller-api
sudo tailscale --socket=/run/tailscale-mullvad-exit/tailscaled.sock status
sudo ip netns exec mullvad-exit wg show wg-mullvad
```

Avoid `wg showconf`, `wg show all dump`, config dumps or pairing-file output when collecting public diagnostics: they can expose private keys/tokens. Closing SSH does not stop enabled services. Keep the VM patched and monitor Tailscale node-key expiry and the Mullvad subscription.

`check-forwarding` is an installation/maintenance test that creates a simulated `tailscale0`; it requires stopping the gateway Tailscale service. Do not run it against a live gateway. Its `blocked` mode also requires WireGuard to be down.

To remove network changes, first stop using the exit node on clients and disable its advertised routes, then:

```sh
sudo systemctl disable --now mullvad-controller-api mullvad-controller-broker
sudo systemctl disable --now tailscaled-mullvad-exit mullvad-exit-wireguard
sudo systemctl disable --now mullvad-exit-network
```

This removes the namespace and dedicated host firewall chains. It leaves the host forwarding sysctl and files/configs on disk; remove those separately only after confirming no other service needs them.

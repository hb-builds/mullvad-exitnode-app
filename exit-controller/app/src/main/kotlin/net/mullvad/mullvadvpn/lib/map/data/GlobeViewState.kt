// SPDX-License-Identifier: GPL-3.0-only
// Adapted from Mullvad VPN; see THIRD_PARTY_NOTICES.md for revision and changes.
package net.mullvad.mullvadvpn.lib.map.data


class GlobeViewState(
    val cameraPosition: CameraPosition,
    val markers: List<Marker> = emptyList(),
    val hops: List<Hop> = emptyList(),
    val globeColors: GlobeColors,
)

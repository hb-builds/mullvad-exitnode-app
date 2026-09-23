// SPDX-License-Identifier: GPL-3.0-only
// Adapted from Mullvad VPN; see THIRD_PARTY_NOTICES.md for revision and changes.
package net.mullvad.mullvadvpn.lib.map.data

import net.mullvad.mullvadvpn.lib.model.LatLong

data class Marker(
    val latLong: LatLong,
    val size: Float = DEFAULT_MARKER_SIZE,
    val colors: LocationMarkerColors,
) {
    companion object {
        private const val DEFAULT_MARKER_SIZE = 0.02f
    }
}

// SPDX-License-Identifier: GPL-3.0-only
// Adapted from Mullvad VPN; see THIRD_PARTY_NOTICES.md for revision and changes.
package net.mullvad.mullvadvpn.lib.map.data

import net.mullvad.mullvadvpn.lib.model.LatLong

data class CameraPosition(
    val latLong: LatLong,
    val zoom: Float = 1.5f,
    val verticalBias: Float = .5f,
    val fov: Float = DEFAULT_FIELD_OF_VIEW,
) {
    companion object {
        const val DEFAULT_FIELD_OF_VIEW = 70f
    }
}

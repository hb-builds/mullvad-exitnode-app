// SPDX-License-Identifier: GPL-3.0-only
// Adapted from Mullvad VPN; see THIRD_PARTY_NOTICES.md for revision and changes.
package net.mullvad.mullvadvpn.lib.map.data

import one.hbx.exitcontroller.map.Color
import net.mullvad.mullvadvpn.lib.model.LatLong

data class Hop(
    val from: LatLong,
    val to: LatLong,
    val color: Color = Color.White.copy(alpha = DEFAULT_ALPHA),
) {
    companion object {
        const val DEFAULT_ALPHA = 0.6f
    }
}

// SPDX-License-Identifier: GPL-3.0-only
// Adapted from Mullvad VPN; see THIRD_PARTY_NOTICES.md for revision and changes.
package net.mullvad.mullvadvpn.lib.map.data

data class Sphere(val center: Vector3, val radius: Float) {
    companion object {
        const val RADIUS = 1f
    }
}

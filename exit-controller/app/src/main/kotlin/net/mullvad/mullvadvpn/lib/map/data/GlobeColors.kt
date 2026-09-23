// SPDX-License-Identifier: GPL-3.0-only
// Adapted from Mullvad VPN; see THIRD_PARTY_NOTICES.md for revision and changes.
package net.mullvad.mullvadvpn.lib.map.data

import one.hbx.exitcontroller.map.Color
import net.mullvad.mullvadvpn.lib.map.internal.toFloatArray

data class GlobeColors(
    val landColor: Color,
    val oceanColor: Color,
    val contourColor: Color = oceanColor,
    val backgroundColor: Color = oceanColor,
) {
    val landColorArray = landColor.toFloatArray()
    val oceanColorArray = oceanColor.toFloatArray()
    val contourColorArray = contourColor.toFloatArray()
}

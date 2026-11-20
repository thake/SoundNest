/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.speakers.connect

import io.github.thake.soundnest.api.Device
import io.github.thake.soundnest.core.playback.SongPlayer
import io.github.thake.soundnest.engine.ConnectDeviceId

/**
 * Pure matching and deduplication rules for Spotify Connect devices, including Amazon Echo
 * devices whose IDs alternate between `<uuid>_amzn_<n>` (REST `hobs`/`cluster`) and
 * `<uuid>::a_<n>` (Harmony dealer updates).
 */
object ConnectTransferMatcher {
    fun matchesDevice(target: SongPlayer.ConnectDevice, candidate: Device): Boolean =
        ConnectDeviceId.sameDevice(
            firstId = target.id,
            secondId = candidate.id,
            firstAliasId = target.aliasId,
            secondAliasId = candidate.aliasId,
        )

    fun matchesChosenRemote(deviceId: String?, chosenRemote: String?, aliasId: String? = null): Boolean =
        ConnectDeviceId.sameDevice(
            firstId = deviceId,
            secondId = chosenRemote,
            firstAliasId = aliasId,
        )

    fun dedupeKey(device: SongPlayer.ConnectDevice): String =
        if (device.isSelf) "self"
        else ConnectDeviceId.parse(device.id, device.aliasId).let { "${it.baseId}:${it.aliasId.orEmpty()}" }
}

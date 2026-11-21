/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.remote.net

import io.github.thake.soundnest.feature.parental.settings.FilterMode
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.RuleLabel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

object RemoteProtocol {
    const val PROTOCOL_VERSION = 1
    const val MDNS_SERVICE_TYPE = "_soundnest-ctrl._tcp."
    const val MAX_FRAME_BYTES = 1024 * 1024 // 1 MB

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    fun aadFor(parentId: String, childId: String): ByteArray =
        "soundnest-remote-v$PROTOCOL_VERSION|$parentId|$childId".toByteArray(Charsets.UTF_8)

    fun writeFrame(out: OutputStream, payload: ByteArray) {
        require(payload.size <= MAX_FRAME_BYTES) { "Frame too large: ${payload.size}" }
        val dos = DataOutputStream(out)
        dos.writeInt(payload.size)
        dos.write(payload)
        dos.flush()
    }

    fun readFrame(input: InputStream): ByteArray {
        val dis = DataInputStream(input)
        val len = dis.readInt()
        require(len in 1..MAX_FRAME_BYTES) { "Invalid frame length: $len" }
        val buf = ByteArray(len)
        dis.readFully(buf)
        return buf
    }

    inline fun <reified T> writeJsonFrame(out: OutputStream, value: T) {
        writeFrame(out, json.encodeToString(value).toByteArray(Charsets.UTF_8))
    }

    inline fun <reified T> readJsonFrame(input: InputStream): T {
        val bytes = readFrame(input)
        return json.decodeFromString(bytes.toString(Charsets.UTF_8))
    }
}

/** First unencrypted message sent by Child Device on every new TCP connection. */
@Serializable
data class ServerHello(
    val version: Int = RemoteProtocol.PROTOCOL_VERSION,
    val childId: String,
    val deviceName: String,
    val challengeNonceB64: String,
    val pairingOpen: Boolean = false,
)

/** Unencrypted outer wire message sent by Parent Device after receiving [ServerHello]. */
@Serializable
sealed class ClientWireMessage {
    @Serializable
    @SerialName("pair_init")
    data class PairInit(
        val parentId: String,
        val parentName: String,
        val parentEcdhPubB64: String,
        val proofHmacB64: String,
    ) : ClientWireMessage()

    @Serializable
    @SerialName("encrypted")
    data class Encrypted(
        val parentId: String,
        val ivB64: String,
        val ciphertextB64: String,
    ) : ClientWireMessage()
}

/** Unencrypted outer wire response sent by Child Device. */
@Serializable
sealed class ServerWireMessage {
    @Serializable
    @SerialName("pair_complete")
    data class PairComplete(
        val childId: String,
        val childName: String,
        val childEcdhPubB64: String,
        val proofHmacB64: String,
    ) : ServerWireMessage()

    @Serializable
    @SerialName("encrypted")
    data class Encrypted(
        val ivB64: String,
        val ciphertextB64: String,
    ) : ServerWireMessage()

    @Serializable
    @SerialName("error")
    data class Error(
        val code: String,
        val message: String,
    ) : ServerWireMessage()
}

/** Encrypted inner request envelope bound to the connection's [challengeNonceB64]. */
@Serializable
data class AuthenticatedRequest(
    val challengeNonceB64: String,
    val request: RemoteRequest,
)

/** Encrypted inner response envelope bound to the connection's [challengeNonceB64]. */
@Serializable
data class AuthenticatedResponse(
    val challengeNonceB64: String,
    val response: RemoteResponse,
)

@Serializable
enum class RemotePlaybackAction {
    PLAY,
    PAUSE,
    NEXT,
    PREVIOUS,
    SET_VOLUME,
    SET_SLEEP_TIMER,
}

@Serializable
sealed class RemoteRequest {
    @Serializable
    @SerialName("get_status")
    data object GetStatus : RemoteRequest()

    @Serializable
    @SerialName("playback_action")
    data class PlaybackAction(
        val action: RemotePlaybackAction,
        val volumePercent: Int? = null,
        /** null = off, 0 = end of track, >0 = minutes. */
        val sleepTimerMinutes: Int? = null,
    ) : RemoteRequest()

    @Serializable
    @SerialName("get_policy")
    data object GetPolicy : RemoteRequest()

    @Serializable
    @SerialName("update_policy")
    data class UpdatePolicy(
        val policy: ParentalPolicy,
        val ruleLabels: Map<String, RuleLabel> = emptyMap(),
        val expectedRevision: Long? = null,
    ) : RemoteRequest()

    @Serializable
    @SerialName("rename_device")
    data class RenameDevice(
        val displayName: String,
    ) : RemoteRequest()
}

@Serializable
data class ChildStatusSnapshot(
    val childId: String,
    val deviceName: String,
    val appVersion: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val coverUrl: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSource: String = "",
    val volumePercent: Int = 50,
    /** null = no timer, -1L = end of track, >0 = remaining milliseconds. */
    val sleepTimerRemainingMs: Long? = null,
    val policyRevision: Long = 0L,
    val filterMode: FilterMode = FilterMode.FILTERED_OPEN,
    val blockExplicit: Boolean = true,
)

@Serializable
data class RemotePolicyBundle(
    val childId: String,
    val deviceName: String,
    val policy: ParentalPolicy,
    val ruleLabels: Map<String, RuleLabel>,
    val policyRevision: Long,
)

@Serializable
sealed class RemoteResponse {
    @Serializable
    @SerialName("status")
    data class Status(val snapshot: ChildStatusSnapshot) : RemoteResponse()

    @Serializable
    @SerialName("policy")
    data class Policy(val bundle: RemotePolicyBundle) : RemoteResponse()

    @Serializable
    @SerialName("policy_updated")
    data class PolicyUpdated(
        val policyRevision: Long,
        val snapshot: ChildStatusSnapshot,
    ) : RemoteResponse()

    @Serializable
    @SerialName("error")
    data class Error(val code: String, val message: String) : RemoteResponse()
}

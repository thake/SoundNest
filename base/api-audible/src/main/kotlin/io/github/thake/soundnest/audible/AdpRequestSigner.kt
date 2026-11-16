/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.audible

import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64

data class AdpSignedHeaders(
    val adpToken: String,
    val algorithm: String,
    val signatureHeader: String,
) {
    fun toHeaderMap(): Map<String, String> = mapOf(
        "x-adp-token" to adpToken,
        "x-adp-alg" to algorithm,
        "x-adp-signature" to signatureHeader,
    )
}

object AdpRequestSigner {
    const val ALGORITHM_HEADER = "SHA256withRSA:1.0"
    private const val PKCS1_HEADER = "-----BEGIN RSA PRIVATE KEY-----"
    private const val PKCS1_FOOTER = "-----END RSA PRIVATE KEY-----"
    private const val PKCS8_HEADER = "-----BEGIN PRIVATE KEY-----"
    private const val PKCS8_FOOTER = "-----END PRIVATE KEY-----"

    private val ISO_UTC_SECONDS: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

    /**
     * ASN.1 AlgorithmIdentifier for `rsaEncryption` (OID `1.2.840.113549.1.1.1` + NULL parameters).
     * Used to wrap Amazon's PKCS#1 `RSAPrivateKey` DER blob into a standard PKCS#8 `PrivateKeyInfo`.
     */
    private val RSA_ALGORITHM_IDENTIFIER = byteArrayOf(
        0x30, 0x0d,
        0x06, 0x09, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d, 0x01, 0x01, 0x01,
        0x05, 0x00,
    )

    fun parsePrivateKeyPem(pem: String): PrivateKey {
        val trimmed = pem.trim()
        val isPkcs1 = trimmed.contains(PKCS1_HEADER)
        val stripped = trimmed
            .replace(PKCS1_HEADER, "")
            .replace(PKCS1_FOOTER, "")
            .replace(PKCS8_HEADER, "")
            .replace(PKCS8_FOOTER, "")
            .replace(Regex("\\s+"), "")
        val der = Base64.getDecoder().decode(stripped)
        val pkcs8Bytes = if (isPkcs1) wrapPkcs1InPkcs8(der) else der
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pkcs8Bytes))
    }

    private fun wrapPkcs1InPkcs8(pkcs1Der: ByteArray): ByteArray {
        val versionZero = byteArrayOf(0x02, 0x01, 0x00)
        val octetString = ByteArrayOutputStream().apply {
            write(0x04)
            writeDerLength(this, pkcs1Der.size)
            write(pkcs1Der)
        }.toByteArray()
        val innerLen = versionZero.size + RSA_ALGORITHM_IDENTIFIER.size + octetString.size
        return ByteArrayOutputStream().apply {
            write(0x30)
            writeDerLength(this, innerLen)
            write(versionZero)
            write(RSA_ALGORITHM_IDENTIFIER)
            write(octetString)
        }.toByteArray()
    }

    private fun writeDerLength(out: ByteArrayOutputStream, length: Int) {
        if (length < 0x80) {
            out.write(length)
        } else {
            val lenBytes = mutableListOf<Int>()
            var rem = length
            while (rem > 0) {
                lenBytes.add(0, rem and 0xff)
                rem = rem ushr 8
            }
            out.write(0x80 or lenBytes.size)
            lenBytes.forEach(out::write)
        }
    }

    fun canonicalString(
        method: String,
        pathAndQuery: String,
        isoTimestamp: String,
        body: String,
        adpToken: String,
    ): String = "${method.uppercase()}\n$pathAndQuery\n$isoTimestamp\n$body\n$adpToken"

    fun sign(
        method: String,
        pathAndQuery: String,
        body: String,
        adpToken: String,
        privateKeyPem: String,
        timestamp: Instant = Instant.now(),
    ): AdpSignedHeaders {
        val isoTs = ISO_UTC_SECONDS.format(timestamp)
        val canonical = canonicalString(method, pathAndQuery, isoTs, body, adpToken)
        val privateKey = parsePrivateKeyPem(privateKeyPem)
        val sigBytes = Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update(canonical.encodeToByteArray())
            sign()
        }
        val sigBase64 = Base64.getEncoder().encodeToString(sigBytes)
        return AdpSignedHeaders(
            adpToken = adpToken,
            algorithm = ALGORITHM_HEADER,
            signatureHeader = "$sigBase64:$isoTs",
        )
    }
}

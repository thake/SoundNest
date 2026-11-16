/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.audible

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPrivateCrtKey
import java.time.Instant
import java.util.Base64

class AdpRequestSignerTest {

    private fun generateKeyPair() = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun toPkcs8Pem(encoded: ByteArray): String {
        val b64 = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(encoded)
        return "-----BEGIN PRIVATE KEY-----\n$b64\n-----END PRIVATE KEY-----"
    }

    /** Encodes an [RSAPrivateCrtKey] as PKCS#1 `RSAPrivateKey` PEM, matching Amazon `/auth/register`. */
    private fun toPkcs1Pem(key: RSAPrivateCrtKey): String {
        fun derInteger(bytes: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(0x02)
            writeDerLength(out, bytes.size)
            out.write(bytes)
            return out.toByteArray()
        }
        val fields = listOf(
            ByteArray(1) { 0 }, // version = 0
            key.modulus.toByteArray(),
            key.publicExponent.toByteArray(),
            key.privateExponent.toByteArray(),
            key.primeP.toByteArray(),
            key.primeQ.toByteArray(),
            key.primeExponentP.toByteArray(),
            key.primeExponentQ.toByteArray(),
            key.crtCoefficient.toByteArray(),
        ).map(::derInteger)
        val content = ByteArrayOutputStream().apply { fields.forEach(::write) }.toByteArray()
        val seq = ByteArrayOutputStream().apply {
            write(0x30)
            writeDerLength(this, content.size)
            write(content)
        }.toByteArray()
        val b64 = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(seq)
        return "-----BEGIN RSA PRIVATE KEY-----\n$b64\n-----END RSA PRIVATE KEY-----"
    }

    private fun writeDerLength(out: ByteArrayOutputStream, len: Int) {
        if (len < 0x80) {
            out.write(len)
        } else {
            val bytes = mutableListOf<Int>()
            var rem = len
            while (rem > 0) {
                bytes.add(0, rem and 0xff)
                rem = rem ushr 8
            }
            out.write(0x80 or bytes.size)
            bytes.forEach(out::write)
        }
    }

    @Test
    fun `parses both PKCS#8 and Amazon PKCS#1 RSA private key PEMs and produces valid SHA256withRSA signature`() {
        val kp = generateKeyPair()
        val pkcs8Pem = toPkcs8Pem(kp.private.encoded)
        val pkcs1Pem = toPkcs1Pem(kp.private as RSAPrivateCrtKey)
        val ts = Instant.parse("2026-11-16T22:30:00Z")

        for (pem in listOf(pkcs8Pem, pkcs1Pem)) {
            val signed = AdpRequestSigner.sign(
                method = "POST",
                pathAndQuery = "/1.0/content/B08G9PRS1K/licenserequest",
                body = """{"consumption_type":"Streaming"}""",
                adpToken = "enc:adp-token-test",
                privateKeyPem = pem,
                timestamp = ts,
            )

            assertEquals("enc:adp-token-test", signed.adpToken)
            assertEquals("SHA256withRSA:1.0", signed.algorithm)
            assertTrue(signed.signatureHeader.endsWith(":2026-11-16T22:30:00Z"))

            val sigBase64 = signed.signatureHeader.removeSuffix(":2026-11-16T22:30:00Z")
            val sigBytes = Base64.getDecoder().decode(sigBase64)

            val expectedCanonical =
                "POST\n/1.0/content/B08G9PRS1K/licenserequest\n2026-11-16T22:30:00Z\n{\"consumption_type\":\"Streaming\"}\nenc:adp-token-test"
            assertEquals(
                expectedCanonical,
                AdpRequestSigner.canonicalString(
                    "POST",
                    "/1.0/content/B08G9PRS1K/licenserequest",
                    "2026-11-16T22:30:00Z",
                    """{"consumption_type":"Streaming"}""",
                    "enc:adp-token-test",
                ),
            )

            val verifier = Signature.getInstance("SHA256withRSA").apply {
                initVerify(kp.public)
                update(expectedCanonical.encodeToByteArray())
            }
            assertTrue(verifier.verify(sigBytes), "Signature must verify against RSA public key")
        }
    }
}

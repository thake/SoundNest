package io.github.thake.soundnest.feature.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CliTest {
    private fun fails(cmd: String, vararg args: String): String =
        runCatching { CliParser.parse(cmd, args.toList()) }.exceptionOrNull().let { (it as? CliError)?.message ?: error("$cmd did not fail: $it") }

    @Test fun argsComeAsJsonOrBase64() {
        assertEquals(listOf("a", "b c"), CliParser.args("""["a","b c"]""", null))
        val b64 = java.util.Base64.getEncoder().encodeToString("""["x","{\"q\":1}"]""".toByteArray())
        assertEquals(listOf("x", """{"q":1}"""), CliParser.args("ignored", b64))
        assertEquals(emptyList<String>(), CliParser.args(null, null))
        assertTrue(runCatching { CliParser.args("""[1]""", null) }.exceptionOrNull() is CliError)
        assertTrue(runCatching { CliParser.args(null, "%%%") }.exceptionOrNull() is CliError)
    }

    @Test fun parsesEachCommand() {
        assertEquals(CliCommand.Status, CliParser.parse("status", emptyList()))
        assertEquals(CliCommand.Play("spotify:track:4uLU6hMCjMI75M1A2tKUQC", null), CliParser.parse("play", listOf("spotify:track:4uLU6hMCjMI75M1A2tKUQC")))
        assertEquals(CliCommand.Nav("settings"), CliParser.parse("nav", listOf("settings")))
        assertEquals(CliCommand.AutoMixSettings(), CliParser.parse("automix.settings", emptyList()))
        assertEquals(CliCommand.AutoMixSettings("style", "blend"), CliParser.parse("automix.settings", listOf("style", "blend")))
        assertEquals(CliCommand.AutoMixSettings("mobile-data", "off"), CliParser.parse("automix.settings", listOf("mobile-data", "off")))
        assertEquals(CliCommand.AutoMixSettings("duration-min", "12"), CliParser.parse("automix.settings", listOf("duration-min", "12")))
        assertEquals(CliCommand.AutoMixSettings("duration-max", "0"), CliParser.parse("automix.settings", listOf("duration-max", "0")))
        assertEquals(CliCommand.AutoMixSettings("performance-priority", "on"), CliParser.parse("automix.settings", listOf("performance-priority", "on")))
        // The Mix Lab's ideas, each by its name, and all of them at once.
        assertEquals(CliCommand.AutoMixSettings("bass-handover", "off"), CliParser.parse("automix.settings", listOf("bass-handover", "off")))
        assertEquals(CliCommand.AutoMixSettings("ideas", "on"), CliParser.parse("automix.settings", listOf("ideas", "on")))
        assertEquals(CliCommand.AutoMixNext, CliParser.parse("automix.next", emptyList()))
    }

    @Test fun refusesBadInput() {
        assertTrue(fails("nope").startsWith("unknown command"))
        assertTrue(fails("ext.enable").startsWith("unknown command"))
        assertTrue(fails("catalog.status").startsWith("unknown command"))
        assertTrue(fails("play", "https://example.com/x").contains("spotify:"))
        assertTrue(fails("nav", "https://example.com").contains("route"))
        assertTrue(fails("pause", "extra").contains("too many"))
        assertTrue(fails("automix.settings", "unknown", "on").contains("unknown setting"))
        assertTrue(fails("automix.settings", "style", "unexpected").contains("must be one of"))
        assertTrue(fails("automix.settings", "length").contains("use <name> <value>"))
        assertTrue(fails("automix.settings", "style", "fade", "extra").contains("too many"))
        assertTrue(fails("automix.settings", "duration-min", "-1").contains("integer from 0 to 90"))
        assertTrue(fails("automix.settings", "duration-max", "91").contains("integer from 0 to 90"))
        assertTrue(fails("automix.settings", "duration-min", "auto").contains("integer from 0 to 90"))
        assertTrue(fails("automix.settings", "performance-priority", "yes").contains("must be one of on, off"))
        assertTrue(fails("automix.next", "extra").contains("too many"))
    }

    @Test fun readsOnlyThePublishedAutoMixSettings() {
        val json = AutoMixSettingsSnapshot(
            enabled = true, segue = false, sessionDj = true, albumOrder = false, podcasts = false,
            harmonic = true, eq = true, show = true, mobileData = false, onNext = "full",
            style = "blend", length = "balanced", tempo = "normal", pulse = "strong",
            durationMin = 10, durationMax = 45, performancePriority = true,
            orpheon = true, ideas = mapOf("bass-handover" to true, "gentle-tempo" to false),
        ).json().jsonObject
        assertEquals(JsonPrimitive(true), json["enabled"])
        assertEquals(JsonPrimitive(false), json["mobile-data"])
        assertEquals(JsonPrimitive("full"), json["on-next"])
        assertEquals(JsonPrimitive("balanced"), json["length"])
        assertEquals(JsonPrimitive(10), json["duration-min"])
        assertEquals(JsonPrimitive(45), json["duration-max"])
        assertEquals(JsonPrimitive(true), json["performance-priority"])
        // The Mix Lab's planner and each of its ideas by name, so a listening session can be checked from the CLI.
        assertEquals(JsonPrimitive(true), json["orpheon"])
        assertEquals(JsonPrimitive(true), json["bass-handover"])
        assertEquals(JsonPrimitive(false), json["gentle-tempo"])
        assertEquals(20, json.size)
    }

    @Test fun masksTokensInText() {
        val t = CliMask.text("GET https://cdn.example/a.flac?token=abcdef123&id=7 Authorization: Bearer abcdefghijk cookie=sess=1; x")
        assertFalse(t, t.contains("abcdef123"))
        assertFalse(t, t.contains("abcdefghijk"))
        assertFalse(t, t.contains("sess=1"))
        assertTrue(t, t.contains("id=7"))
        assertEquals("seen *** here", CliMask.text("seen supersecretvalue here", listOf("supersecretvalue", "abc")))
        assertEquals("short abc", CliMask.text("short abc", listOf("abc")))
        assertFalse(CliMask.text("jwt eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.sig").contains("eyJ"))
    }

    @Test fun masksSensitiveJsonFields() {
        val e = CliMask.json(Json.parseToJsonElement("""{"url":"https://x/y?sig=zzzzzz","accessToken":"t0k3n","author":"Someone","nested":{"cookie":"c"},"n":3}"""))
        val o = e.jsonObject
        assertEquals(CliMask.MASK, o["accessToken"]!!.jsonPrimitive.content)
        assertEquals(CliMask.MASK, o["nested"]!!.jsonObject["cookie"]!!.jsonPrimitive.content)
        assertEquals("Someone", o["author"]!!.jsonPrimitive.content)
        assertEquals("https://x/y?sig=${CliMask.MASK}", o["url"]!!.jsonPrimitive.content)
        assertEquals(JsonPrimitive(3), o["n"])
    }

    @Test fun replies() {
        val ok = Json.parseToJsonElement(CliReply.ok("status", JsonPrimitive(1))).jsonObject
        assertEquals(JsonPrimitive(true), ok["ok"])
        val err = Json.parseToJsonElement(CliReply.error("x", "no")).jsonObject
        assertEquals(JsonPrimitive("no"), err["error"])
        assertEquals("a-1_B", CliReply.safeRequestId("a-1_B"))
        assertEquals(null, CliReply.safeRequestId("../x"))
    }
}

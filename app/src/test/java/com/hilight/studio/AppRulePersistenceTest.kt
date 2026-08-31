package com.hilight.studio

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRulePersistenceTest {

    @Test
    fun `face-down notification rule survives preference round trip`() {
        val original = AppRule(
            pkg = "com.example",
            label = "Example",
            trigger = Trigger.NOTIFICATION,
            onlyWhenFaceDown = true,
        )

        assertTrue(AppRule.fromJson(original.toPrefsJson()).onlyWhenFaceDown)
    }

    @Test
    fun `older stored rules default face-down gate to off`() {
        val legacy = JSONObject()
            .put("pkg", "com.example")
            .put("label", "Example")

        assertFalse(AppRule.fromJson(legacy).onlyWhenFaceDown)
    }

    @Test
    fun `exportRulesDocument produces valid json with version and rules`() {
        val rule1 = AppRule(
            pkg = "com.example.chat",
            label = "ChatApp",
            trigger = Trigger.NOTIFICATION,
            pattern = Pattern.BREATHE,
            color = 0xFF123456.toInt(),
            durationMs = 15_000,
            conversationKey = "chat123",
            conversationName = "Alice",
        )
        val rule2 = AppRule(
            pkg = "com.example.mail",
            label = "MailApp",
            trigger = Trigger.FOREGROUND,
            pattern = Pattern.RAINBOW,
        )

        val exported = exportRulesDocument(listOf(rule1, rule2))
        val json = JSONObject(exported)

        assertEquals(1, json.getInt("v"))
        val arr = json.getJSONArray("rules")
        assertEquals(2, arr.length())

        val parsed1 = AppRule.fromJson(arr.getJSONObject(0))
        assertEquals("com.example.chat", parsed1.pkg)
        assertEquals("ChatApp", parsed1.label)
        assertEquals(Trigger.NOTIFICATION, parsed1.trigger)
        assertEquals(Pattern.BREATHE, parsed1.pattern)
        assertEquals(0xFF123456.toInt(), parsed1.color)
        assertEquals(15_000, parsed1.durationMs)
        assertEquals("chat123", parsed1.conversationKey)
        assertEquals("Alice", parsed1.conversationName)

        val parsed2 = AppRule.fromJson(arr.getJSONObject(1))
        assertEquals("com.example.mail", parsed2.pkg)
        assertEquals(Trigger.FOREGROUND, parsed2.trigger)
        assertEquals(Pattern.RAINBOW, parsed2.pattern)
    }

    @Test
    fun `parseImportedRules parses versioned object and raw array format`() {
        val rule = AppRule(
            pkg = "com.example.app",
            label = "TestApp",
            trigger = Trigger.NOTIFICATION,
            pattern = Pattern.COMET,
        )
        val exported = exportRulesDocument(listOf(rule))

        val parsedFromObject = parseImportedRules(exported)
        assertNotNull(parsedFromObject)
        assertEquals(1, parsedFromObject?.size)
        assertEquals(rule.id, parsedFromObject?.first()?.id)

        // Raw array format
        val rawArray = JSONObject(exported).getJSONArray("rules").toString()
        val parsedFromArray = parseImportedRules(rawArray)
        assertNotNull(parsedFromArray)
        assertEquals(1, parsedFromArray?.size)
        assertEquals(rule.id, parsedFromArray?.first()?.id)
    }

    @Test
    fun `parseImportedRules returns null on invalid or unparseable input`() {
        assertNull(parseImportedRules("not valid json"))
        assertNull(parseImportedRules("{}"))
        assertNull(parseImportedRules("""{"rules": [{"invalid": true}]}"""))
        assertNull(parseImportedRules("""[{"invalid": true}]"""))
    }

    @Test
    fun `parseImportedRules returns empty list for empty rules array`() {
        val result = parseImportedRules("""{"v": 1, "rules": []}""")
        assertNotNull(result)
        assertTrue(result?.isEmpty() == true)

        val arrayResult = parseImportedRules("[]")
        assertNotNull(arrayResult)
        assertTrue(arrayResult?.isEmpty() == true)
    }

    @Test
    fun `mergeImportedRules updates existing rule in place and preserves list order`() {
        val existing1 = AppRule(pkg = "com.app.one", label = "One", color = 0x111111)
        val existing2 = AppRule(pkg = "com.app.two", label = "Two", color = 0x222222)
        val updated2 = existing2.copy(color = 0x999999, pattern = Pattern.CHASE)

        val merged = mergeImportedRules(listOf(existing1, existing2), listOf(updated2))

        assertEquals(2, merged.size)
        assertEquals(existing1.id, merged[0].id)
        assertEquals(0x111111, merged[0].color)
        assertEquals(existing2.id, merged[1].id)
        assertEquals(0x999999, merged[1].color)
        assertEquals(Pattern.CHASE, merged[1].pattern)
    }

    @Test
    fun `mergeImportedRules appends new rules at the end`() {
        val existing1 = AppRule(pkg = "com.app.one", label = "One")
        val newRule = AppRule(pkg = "com.app.new", label = "New")

        val merged = mergeImportedRules(listOf(existing1), listOf(newRule))

        assertEquals(2, merged.size)
        assertEquals("com.app.one", merged[0].pkg)
        assertEquals("com.app.new", merged[1].pkg)
    }

    @Test
    fun `mergeImportedRules deduplicates incoming rules with same id`() {
        val incoming1 = AppRule(pkg = "com.app.one", label = "One", color = 0x111111)
        val incoming2 = AppRule(pkg = "com.app.one", label = "One", color = 0x222222)

        val merged = mergeImportedRules(emptyList(), listOf(incoming1, incoming2))

        assertEquals(1, merged.size)
        assertEquals(0x222222, merged[0].color)
    }
}


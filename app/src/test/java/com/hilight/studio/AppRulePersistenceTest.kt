package com.hilight.studio

import org.json.JSONObject
import org.junit.Assert.assertFalse
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
        assertFalse(AppRule.fromJson(legacy).patternSound)
    }

    @Test
    fun `patternSound survives round trip and defaults to false`() {
        val withSound = AppRule(
            pkg = "com.example",
            label = "Example",
            patternSound = true,
        )
        assertTrue(AppRule.fromJson(withSound.toPrefsJson()).patternSound)

        val withoutSound = AppRule(
            pkg = "com.example",
            label = "Example",
        )
        assertFalse(withoutSound.patternSound)
        assertFalse(AppRule.fromJson(withoutSound.toPrefsJson()).patternSound)
    }
}

package com.andrerinas.openheadunit.connection.carkey.fyt

import org.junit.Assert.assertEquals
import org.junit.Test

class FytKeyTranslationPolicyTest {
    @Test fun allSevenRawValuesTranslate() {
        val expected = mapOf(547 to 23, 550 to 21, 551 to 22, 574 to 87, 575 to 88, 587 to 88, 588 to 87)
        for ((raw, key) in expected) {
            assertEquals(raw.toString(), key, FytKeyTranslationPolicy.sendCode(raw, emptyMap(), emptySet()))
        }
    }

    @Test fun anUnknownValueStaysRaw() {
        assertEquals(576, FytKeyTranslationPolicy.sendCode(576, emptyMap(), emptySet()))
    }

    @Test fun aMappedRawValueStaysRaw() {
        assertEquals(547, FytKeyTranslationPolicy.sendCode(547, mapOf(66 to 547), emptySet()))
    }

    @Test fun anIgnoredRawValueStaysRaw() {
        assertEquals(547, FytKeyTranslationPolicy.sendCode(547, emptyMap(), setOf(547)))
    }
}

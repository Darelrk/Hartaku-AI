package com.example.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests [UpdateChecker.isNewer] — the pure comparison behind the "Cek Update"
 * dialog. No network: only the version normalization matters here.
 */
class UpdateCheckerTest {

    @Test
    fun newerPatch_isNewer() {
        assertTrue(UpdateChecker.isNewer("1.0.1", "1.0.0"))
    }

    @Test
    fun sameVersion_isNotNewer() {
        assertFalse(UpdateChecker.isNewer("1.0.0", "1.0.0"))
    }

    @Test
    fun olderVersion_isNotNewer() {
        assertFalse(UpdateChecker.isNewer("1.0.0", "1.0.1"))
    }

    @Test
    fun gitTagPrefix_isIgnored() {
        assertTrue(UpdateChecker.isNewer("v2.0.0", "1.9.9"))
    }

    @Test
    fun nonNumericSuffix_isIgnored() {
        assertFalse(UpdateChecker.isNewer("1.0.0", "1.0.0-internal"))
    }

    @Test
    fun differentSegmentCount_padsWithZero() {
        assertTrue(UpdateChecker.isNewer("1.1", "1.0.9"))
    }
}

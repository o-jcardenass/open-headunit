package com.andrerinas.openheadunit.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class IgnoredKeyCodesBackupTest {
    @Test
    fun ignoredKeysAreBackedUpAsAStringSet() {
        assertEquals(
            SettingsBackupManager.ValueType.STRING_SET,
            SettingsBackupManager.backupKeys["ignored-key-codes"]
        )
    }
}

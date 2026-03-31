package blbl.cat3399.core.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppPrefsTest {
    @Test
    fun normalizePlayerAutoSkipServerBaseUrl_should_trim_and_remove_trailing_slash() {
        val normalized = AppPrefs.normalizePlayerAutoSkipServerBaseUrl("  https://bsbsb.top/  ")

        assertEquals("https://bsbsb.top", normalized)
    }

    @Test
    fun normalizePlayerAutoSkipServerBaseUrl_should_keep_http_ip_address() {
        val normalized = AppPrefs.normalizePlayerAutoSkipServerBaseUrl("http://154.222.28.109/")

        assertEquals("http://154.222.28.109", normalized)
    }

    @Test
    fun normalizePlayerAutoSkipServerBaseUrl_should_reject_invalid_values() {
        assertNull(AppPrefs.normalizePlayerAutoSkipServerBaseUrl(""))
        assertNull(AppPrefs.normalizePlayerAutoSkipServerBaseUrl("bsbsb.top"))
        assertNull(AppPrefs.normalizePlayerAutoSkipServerBaseUrl("https://bsbsb.top/api?x=1"))
    }
}

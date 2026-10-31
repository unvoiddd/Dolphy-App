package com.droid.dolphy.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL

class PluginUrlPolicyTest {

    @Test
    fun `bare host is upgraded to https`() {
        val url = PluginUrlPolicy.normalize("example.com/wifi.dolphyplugin")

        assertNotNull(url)
        assertEquals("https", url!!.protocol)
        assertEquals("example.com", url.host)
    }

    @Test
    fun `plain http is rejected instead of upgraded`() {
        assertNull(PluginUrlPolicy.normalize("http://example.com/plugin.dolphyplugin"))
    }

    @Test
    fun `non http schemes are rejected`() {
        assertNull(PluginUrlPolicy.normalize("file:///sdcard/plugin.dolphyplugin"))
        assertNull(PluginUrlPolicy.normalize("content://com.example/plugin.dolphyplugin"))
        assertNull(PluginUrlPolicy.normalize("ftp://example.com/plugin.dolphyplugin"))
    }

    @Test
    fun `blank input is rejected`() {
        assertNull(PluginUrlPolicy.normalize("   "))
    }

    @Test
    fun `only https urls with a host are allowed`() {
        assertTrue(PluginUrlPolicy.isAllowed(URL("https://example.com/a.dolphyplugin")))
        assertFalse(PluginUrlPolicy.isAllowed(URL("http://example.com/a.dolphyplugin")))
    }

    @Test
    fun `relative redirect is resolved against https base`() {
        val base = URL("https://example.com/plugins/index.dolphyplugin")
        val resolved = PluginUrlPolicy.resolveRedirect(base, "/next/plugin.dolphyplugin")

        assertNotNull(resolved)
        assertEquals("https", resolved!!.protocol)
        assertEquals("https://example.com/next/plugin.dolphyplugin", resolved.toString())
    }

    @Test
    fun `redirect that downgrades to http is rejected`() {
        val base = URL("https://example.com/plugin.dolphyplugin")

        assertNull(PluginUrlPolicy.resolveRedirect(base, "http://example.com/plugin.dolphyplugin"))
        assertNull(PluginUrlPolicy.resolveRedirect(base, "file:///data/local/tmp/plugin.dolphyplugin"))
    }

    @Test
    fun `same origin detection ignores default port`() {
        assertTrue(
            PluginUrlPolicy.isSameOrigin(
                URL("https://example.com:443/a"),
                URL("https://example.com/b"),
            ),
        )
        assertFalse(
            PluginUrlPolicy.isSameOrigin(
                URL("https://example.com/a"),
                URL("https://cdn.example.net/a"),
            ),
        )
    }
}

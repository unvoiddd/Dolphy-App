package com.droid.dolphy.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalHttpPolicyTest {

    @Test
    fun acceptsPrivateAndLoopbackAddresses() {
        assertTrue(LocalHttpPolicy.isLocalHost("127.0.0.1"))
        assertTrue(LocalHttpPolicy.isLocalHost("10.1.2.3"))
        assertTrue(LocalHttpPolicy.isLocalHost("172.16.0.10"))
        assertTrue(LocalHttpPolicy.isLocalHost("192.168.1.50"))
        assertTrue(LocalHttpPolicy.isLocalHost("169.254.1.1"))
        assertTrue(LocalHttpPolicy.isLocalHost("localhost"))
        assertTrue(LocalHttpPolicy.isLocalHost("printer.local"))
    }

    @Test
    fun rejectsPublicAddresses() {
        assertFalse(LocalHttpPolicy.isLocalHost("8.8.8.8"))
        assertFalse(LocalHttpPolicy.isLocalHost("1.1.1.1"))
        assertFalse(LocalHttpPolicy.isLocalHost("example.com"))
        assertFalse(LocalHttpPolicy.isLocalHost(""))
    }

    @Test
    fun validatesUrlsAndSchemes() {
        assertTrue(LocalHttpPolicy.isLocalUrl("http://192.168.1.20:8080/status"))
        assertTrue(LocalHttpPolicy.isLocalUrl("https://router.local/admin"))
        assertFalse(LocalHttpPolicy.isLocalUrl("ftp://192.168.1.20/file"))
        assertFalse(LocalHttpPolicy.isLocalUrl("http://8.8.8.8/"))
        assertFalse(LocalHttpPolicy.isLocalUrl("not a url"))
    }
}

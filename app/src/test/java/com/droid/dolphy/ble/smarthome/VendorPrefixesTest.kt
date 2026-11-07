package com.droid.dolphy.ble.smarthome
import org.junit.Assert.*
import org.junit.Test
import java.io.File
class VendorPrefixesTest {
    @Test fun spacesTabsAndNamesWithSpacesAreParsed() {
        assertEquals("000000" to "Xerox Corporation", VendorPrefixes.parseLine("000000 Xerox Corporation"))
        assertEquals("AABBCC" to "Example Device Vendor", VendorPrefixes.parseLine("  aabbcc\tExample Device Vendor  "))
    }
    @Test fun commentsAndMalformedPrefixesAreIgnored() {
        for (line in listOf("", " # comment", "invalid Vendor", "00000 Vendor", "AAAAAA")) assertNull(VendorPrefixes.parseLine(line))
    }
    @Test fun bundledDatabaseLoadsRealEntries() {
        val entries = File("src/main/assets/ble/nmap-mac-prefixes.txt").useLines { it.mapNotNull(VendorPrefixes::parseLine).toMap() }
        assertTrue("Expected all three IEEE prefix lengths", entries.size > 50_000)
        assertEquals("Xerox", VendorPrefixes.lookup(entries, "00:00:00:00:00:01"))
    }

    @Test fun longestPrefixWinsAndUnknownAddressIsHandled() {
        val entries = mapOf("AABBCC" to "Large", "AABBCCD" to "Medium", "AABBCCDDE" to "Small")
        assertEquals("Small", VendorPrefixes.lookup(entries, "AA:BB:CC:DD:EE:FF"))
        assertEquals("Medium", VendorPrefixes.lookup(entries, "AA:BB:CC:DF:00:00"))
        assertEquals("Large", VendorPrefixes.lookup(entries, "AA:BB:CC:00:00:00"))
        assertNull(VendorPrefixes.lookup(entries, "00:11:22:33:44:55"))
        assertNull(VendorPrefixes.lookup(entries, "invalid"))
    }
}

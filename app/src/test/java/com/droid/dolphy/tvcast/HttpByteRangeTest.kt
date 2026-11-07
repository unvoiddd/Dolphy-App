package com.droid.dolphy.tvcast
import org.junit.Assert.*
import org.junit.Test
class HttpByteRangeTest {
    @Test fun closedRangeHasCorrectLength() { val range = HttpByteRange.parse("bytes=10-19", 100)!!; assertEquals(HttpByteRange(10, 19), range); assertEquals(10L, range.length) }
    @Test fun openRangeExtendsToEnd() { assertEquals(HttpByteRange(10, 99), HttpByteRange.parse("bytes=10-", 100)) }
    @Test fun suffixAndOversizedEndAreClamped() {
        assertEquals(HttpByteRange(90, 99), HttpByteRange.parse("bytes=-10", 100))
        assertEquals(HttpByteRange(0, 99), HttpByteRange.parse("bytes=-200", 100))
        assertEquals(HttpByteRange(10, 99), HttpByteRange.parse("bytes=10-200", 100))
    }
    @Test fun impossibleOrUnsupportedRangesAreRejected() {
        for (header in listOf("bytes=100-", "bytes=20-10", "bytes=-0", "bytes=0-1,3-4", "bytes=x-10", "items=0-1", "bytes=9223372036854775808-")) assertNull(header, HttpByteRange.parse(header, 100))
        assertNull(HttpByteRange.parse("bytes=0-0", 0))
    }
}

package com.example.pocketserver.core.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;

public class RangeParserTest {

    @Test
    public void testNullOrInvalidHeaderReturnsNull() throws Exception {
        assertNull(RangeParser.parse(null, 1000));
        assertNull(RangeParser.parse("", 1000));
        assertNull(RangeParser.parse("characters=0-100", 1000));
    }

    @Test
    public void testExplicitRange() throws Exception {
        RangeParser.ByteRange range = RangeParser.parse("bytes=0-499", 1000);
        assertNotNull(range);
        assertEquals(0, range.getStart());
        assertEquals(499, range.getEnd());
        assertEquals(500, range.getLength());
        assertEquals("bytes 0-499/1000", range.toContentRangeHeader());
    }

    @Test
    public void testOpenRangeToEOF() throws Exception {
        RangeParser.ByteRange range = RangeParser.parse("bytes=500-", 1000);
        assertNotNull(range);
        assertEquals(500, range.getStart());
        assertEquals(999, range.getEnd());
        assertEquals(500, range.getLength());
        assertEquals("bytes 500-999/1000", range.toContentRangeHeader());
    }

    @Test
    public void testSuffixRange() throws Exception {
        RangeParser.ByteRange range = RangeParser.parse("bytes=-300", 1000);
        assertNotNull(range);
        assertEquals(700, range.getStart());
        assertEquals(999, range.getEnd());
        assertEquals(300, range.getLength());
        assertEquals("bytes 700-999/1000", range.toContentRangeHeader());
    }

    @Test
    public void testClampsRangeExceedingTotalLength() throws Exception {
        RangeParser.ByteRange range = RangeParser.parse("bytes=0-2000", 1000);
        assertNotNull(range);
        assertEquals(0, range.getStart());
        assertEquals(999, range.getEnd());
        assertEquals(1000, range.getLength());
    }

    @Test
    public void testThrowsWhenRangeOutsideBounds() {
        try {
            RangeParser.parse("bytes=1500-2000", 1000);
            fail("Expected RangeNotSatisfiableException");
        } catch (RangeParser.RangeNotSatisfiableException e) {
            assertEquals(1000, e.getTotalLength());
        }

        try {
            RangeParser.parse("bytes=500-200", 1000);
            fail("Expected RangeNotSatisfiableException for start > end");
        } catch (RangeParser.RangeNotSatisfiableException e) {
            assertEquals(1000, e.getTotalLength());
        }
    }
}

package com.example.pocketserver.engine.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for RequestLogItem model and its formatting methods.
 */
public class RequestLogItemTest {

    @Test
    public void testRequestLogItemCreationAndAccessors() {
        long now = 1728260000000L;
        RequestLogItem item = new RequestLogItem(
                "req_101",
                "GET",
                "/index.html",
                200,
                4096,
                24,
                now
        );

        assertEquals("req_101", item.getRequestId());
        assertEquals("GET", item.getMethod());
        assertEquals("/index.html", item.getPath());
        assertEquals(200, item.getStatusCode());
        assertEquals(4096, item.getBytes());
        assertEquals(24, item.getDurationMs());
        assertEquals(now, item.getTimestamp());

        assertEquals("4.0 KB", item.getFormattedBytes());
        assertNotNull(item.getFormattedTime());
        assertTrue(item.getFormattedTime().contains(":"));
    }

    @Test
    public void testFormattedBytesRanges() {
        RequestLogItem bytesItem = new RequestLogItem("req_1", "GET", "/", 200, 500, 5, 0);
        assertEquals("500 B", bytesItem.getFormattedBytes());

        RequestLogItem kbItem = new RequestLogItem("req_2", "GET", "/", 200, 1500, 5, 0);
        assertEquals("1.5 KB", kbItem.getFormattedBytes());

        RequestLogItem mbItem = new RequestLogItem("req_3", "GET", "/", 200, 2 * 1024 * 1024, 5, 0);
        assertEquals("2.0 MB", mbItem.getFormattedBytes());
    }
}

package com.example.pocketserver.tunnel.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/**
 * Unit tests for TunnelControlFrame and FrameParser serialization and validation.
 */
public class TunnelControlFrameTest {

    @Test
    public void testRegisterAndRegisterOkFrames() {
        TunnelControlFrame register = TunnelControlFrame.register("dep_123", "proj_456", "jwt_token", "mysite.dev");
        String json = register.toJson();
        TunnelControlFrame parsed = FrameParser.parseTextFrame(json);

        assertNotNull(parsed);
        assertEquals(FrameType.REGISTER, parsed.getType());
        assertEquals("dep_123", parsed.getDeploymentId());
        assertEquals("proj_456", parsed.getProjectId());
        assertEquals("jwt_token", parsed.getToken());
        assertEquals("mysite.dev", parsed.getHostname());

        TunnelControlFrame okFrame = TunnelControlFrame.registerOk("https://mysite.dev", "mysite.dev");
        TunnelControlFrame parsedOk = FrameParser.parseTextFrame(okFrame.toJson());
        assertNotNull(parsedOk);
        assertEquals(FrameType.REGISTER_OK, parsedOk.getType());
        assertEquals("https://mysite.dev", parsedOk.getPublicUrl());
        assertEquals("mysite.dev", parsedOk.getHostname());
    }

    @Test
    public void testRequestStartAndResponseStartFrames() {
        Map<String, String> reqHeaders = new HashMap<>();
        reqHeaders.put("Accept", "text/html");
        reqHeaders.put("User-Agent", "Mozilla/5.0");

        TunnelControlFrame reqStart = TunnelControlFrame.requestStart(
                "req_99",
                "GET",
                "/about.html",
                reqHeaders,
                "ref=demo",
                "192.168.1.10"
        );
        String reqJson = reqStart.toJson();
        TunnelControlFrame parsedReq = FrameParser.parseTextFrame(reqJson);

        assertNotNull(parsedReq);
        assertEquals(FrameType.REQUEST_START, parsedReq.getType());
        assertEquals("req_99", parsedReq.getRequestId());
        assertEquals("GET", parsedReq.getMethod());
        assertEquals("/about.html", parsedReq.getPath());
        assertEquals("text/html", parsedReq.getHeaders().get("Accept"));
        assertEquals("ref=demo", parsedReq.getQueryString());
        assertEquals("192.168.1.10", parsedReq.getClientIp());

        Map<String, String> respHeaders = new HashMap<>();
        respHeaders.put("Content-Type", "text/html; charset=utf-8");
        respHeaders.put("ETag", "\"etag-1\"");

        TunnelControlFrame respStart = TunnelControlFrame.responseStart(
                "req_99",
                200,
                "OK",
                respHeaders,
                4096L
        );
        TunnelControlFrame parsedResp = FrameParser.parseTextFrame(respStart.toJson());
        assertNotNull(parsedResp);
        assertEquals(FrameType.RESPONSE_START, parsedResp.getType());
        assertEquals("req_99", parsedResp.getRequestId());
        assertEquals(200, parsedResp.getStatusCode());
        assertEquals("OK", parsedResp.getStatusMessage());
        assertEquals(4096L, parsedResp.getContentLength());
        assertEquals("\"etag-1\"", parsedResp.getHeaders().get("ETag"));
    }

    @Test
    public void testStreamEndAndRequestCancelFrames() {
        TunnelControlFrame end = TunnelControlFrame.streamEnd("req_77");
        TunnelControlFrame parsedEnd = FrameParser.parseTextFrame(end.toJson());
        assertNotNull(parsedEnd);
        assertEquals(FrameType.STREAM_END, parsedEnd.getType());
        assertEquals("req_77", parsedEnd.getRequestId());

        TunnelControlFrame cancel = TunnelControlFrame.requestCancel("req_77");
        TunnelControlFrame parsedCancel = FrameParser.parseTextFrame(cancel.toJson());
        assertNotNull(parsedCancel);
        assertEquals(FrameType.REQUEST_CANCEL, parsedCancel.getType());
        assertEquals("req_77", parsedCancel.getRequestId());
    }

    @Test
    public void testPingPongAndErrorFrames() {
        TunnelControlFrame ping = TunnelControlFrame.ping();
        assertEquals(FrameType.PING, FrameParser.parseTextFrame(ping.toJson()).getType());

        TunnelControlFrame pong = TunnelControlFrame.pong();
        assertEquals(FrameType.PONG, FrameParser.parseTextFrame(pong.toJson()).getType());

        TunnelControlFrame err = TunnelControlFrame.error("req_55", 429, "Rate limit exceeded");
        TunnelControlFrame parsedErr = FrameParser.parseTextFrame(err.toJson());
        assertNotNull(parsedErr);
        assertEquals(FrameType.ERROR, parsedErr.getType());
        assertEquals(429, parsedErr.getErrorCode());
        assertEquals("Rate limit exceeded", parsedErr.getErrorMessage());
    }

    @Test
    public void testMalformedFrameReturnsNull() {
        assertNull(FrameParser.parseTextFrame(""));
        assertNull(FrameParser.parseTextFrame("   "));
        assertNull(FrameParser.parseTextFrame("{ not a valid json }"));
        assertNull(FrameParser.parseTextFrame("{\"someField\": 123}")); // missing type
    }
}

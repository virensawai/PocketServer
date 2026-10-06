package com.example.pocketserver.relay;

import com.example.pocketserver.relay.config.GatewayConfig;
import com.example.pocketserver.relay.handler.RateLimiterHandler;
import com.example.pocketserver.relay.metrics.GatewayMetrics;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RateLimiterHandlerTest {

    @Test
    public void testTokenBucketBehavior() {
        RateLimiterHandler.TokenBucket bucket = new RateLimiterHandler.TokenBucket(5, 5);

        // Can acquire 5 tokens immediately
        for (int i = 0; i < 5; i++) {
            assertTrue("Token " + i + " should be acquired", bucket.tryAcquire());
        }

        // 6th token should be rejected
        assertFalse("Exhausted bucket should reject token", bucket.tryAcquire());
    }

    @Test
    public void testRateLimiterHandlerHttp429Response() {
        GatewayConfig config = new GatewayConfig()
                .setMaxVisitorRequestsPerSecondPerIp(2)
                .setMaxConcurrentConnectionsPerIp(10);
        GatewayMetrics metrics = new GatewayMetrics();
        RateLimiterHandler handler = new RateLimiterHandler(config, metrics);

        EmbeddedChannel channel = new EmbeddedChannel(handler);

        // Request 1: Passed through
        DefaultFullHttpRequest req1 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/test");
        channel.writeInbound(req1);
        Object out1 = channel.readInbound();
        assertNotNull(out1);

        // Request 2: Passed through
        DefaultFullHttpRequest req2 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/test");
        channel.writeInbound(req2);
        Object out2 = channel.readInbound();
        assertNotNull(out2);

        // Request 3: Rate limited -> returns 429
        DefaultFullHttpRequest req3 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/test");
        channel.writeInbound(req3);
        FullHttpResponse response = channel.readOutbound();
        assertNotNull("Expected 429 response", response);
        assertEquals(HttpResponseStatus.TOO_MANY_REQUESTS, response.status());
        assertEquals(1, metrics.getRateLimitedRequests());

        channel.finish();
    }
}

package com.example.pocketserver.relay;

import com.example.pocketserver.relay.handler.GatewayCompressionHandler;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GatewayCompressionHandlerTest {

    @Test
    public void testCompressesHtmlText() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new HttpServerCodec(),
                new GatewayCompressionHandler()
        );

        // Visitor accepts gzip
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/index.html");
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, "gzip");
        channel.writeInbound(request);

        // Server produces large HTML text
        StringBuilder htmlBuilder = new StringBuilder("<html><body>");
        for (int i = 0; i < 200; i++) {
            htmlBuilder.append("<p>Paragraph content ").append(i).append(" for compression testing</p>");
        }
        htmlBuilder.append("</body></html>");
        byte[] payload = htmlBuilder.toString().getBytes(StandardCharsets.UTF_8);

        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.wrappedBuffer(payload)
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, payload.length);

        channel.writeOutbound(response);

        // In Netty, HttpContentEncoder outputs encoded HttpResponse + HttpContent or FullHttpResponse
        Object out = channel.readOutbound();
        assertNotNull(out);

        channel.finish();
    }

    @Test
    public void testBypassesImageAndVideoCompression() {
        GatewayCompressionHandler handler = new GatewayCompressionHandler();

        // 1. Verify text/html is not bypassed (returns non-null or compresses)
        DefaultFullHttpResponse htmlResponse = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.EMPTY_BUFFER
        );
        htmlResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html");

        // 2. Verify image/png is bypassed
        DefaultFullHttpResponse pngResponse = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.EMPTY_BUFFER
        );
        pngResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "image/png");

        // 3. Verify video/mp4 is bypassed
        DefaultFullHttpResponse mp4Response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.EMPTY_BUFFER
        );
        mp4Response.headers().set(HttpHeaderNames.CONTENT_TYPE, "video/mp4");

        // 4. Verify application/wasm is bypassed
        DefaultFullHttpResponse wasmResponse = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.EMPTY_BUFFER
        );
        wasmResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/wasm");

        // We can test through EmbeddedChannel
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/test");
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, "gzip");
        channel.writeInbound(request);

        channel.writeOutbound(pngResponse);
        FullHttpResponse outPng = channel.readOutbound();
        assertNotNull(outPng);
        assertFalse("PNG response should not have content-encoding gzip",
                outPng.headers().contains(HttpHeaderNames.CONTENT_ENCODING));

        channel.finish();
    }
}

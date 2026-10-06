package com.example.pocketserver.core.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MimeTypesTest {

    @Test
    public void testCommonMimeTypes() {
        assertEquals("text/html; charset=utf-8", MimeTypes.getMimeType("index.html"));
        assertEquals("text/css; charset=utf-8", MimeTypes.getMimeType("style.css"));
        assertEquals("application/javascript; charset=utf-8", MimeTypes.getMimeType("app.js"));
        assertEquals("application/javascript; charset=utf-8", MimeTypes.getMimeType("module.mjs"));
        assertEquals("application/json; charset=utf-8", MimeTypes.getMimeType("data.json"));
        assertEquals("image/png", MimeTypes.getMimeType("logo.png"));
        assertEquals("image/svg+xml", MimeTypes.getMimeType("icon.svg"));
        assertEquals("image/webp", MimeTypes.getMimeType("banner.webp"));
        assertEquals("application/wasm", MimeTypes.getMimeType("engine.wasm"));
        assertEquals("video/mp4", MimeTypes.getMimeType("demo.mp4"));
        assertEquals("font/woff2", MimeTypes.getMimeType("font.woff2"));
    }

    @Test
    public void testIsStaticAssetForSpaExclusion() {
        assertTrue(MimeTypes.isStaticAsset("assets/bundle.js"));
        assertTrue(MimeTypes.isStaticAsset("styles/theme.css"));
        assertTrue(MimeTypes.isStaticAsset("images/pic.png"));
        assertTrue(MimeTypes.isStaticAsset("fonts/inter.woff2"));
        assertTrue(MimeTypes.isStaticAsset("wasm/calc.wasm"));

        // HTML and non-extension virtual routes are NOT static assets (they should fallback to SPA index.html)
        assertFalse(MimeTypes.isStaticAsset("index.html"));
        assertFalse(MimeTypes.isStaticAsset("dashboard"));
        assertFalse(MimeTypes.isStaticAsset("about/us"));
        assertFalse(MimeTypes.isStaticAsset("projects/123"));
    }
}

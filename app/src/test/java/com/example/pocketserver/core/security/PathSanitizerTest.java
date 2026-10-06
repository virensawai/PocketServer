package com.example.pocketserver.core.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;

public class PathSanitizerTest {

    private PathSanitizer sanitizer;

    @Before
    public void setUp() {
        sanitizer = new PathSanitizer();
    }

    @Test
    public void testRootAndEmptyPathsResolveToIndexHtml() {
        assertEquals("index.html", sanitizer.sanitize("/"));
        assertEquals("index.html", sanitizer.sanitize(""));
        assertEquals("index.html", sanitizer.sanitize(null));
        assertEquals("index.html", sanitizer.sanitize("//"));
        assertEquals("index.html", sanitizer.sanitize("/./"));
    }

    @Test
    public void testStandardValidPaths() {
        assertEquals("index.html", sanitizer.sanitize("/index.html"));
        assertEquals("assets/main.css", sanitizer.sanitize("/assets/main.css"));
        assertEquals("js/bundle.js", sanitizer.sanitize("js/bundle.js"));
        assertEquals("deep/nested/folder/file.json", sanitizer.sanitize("/deep/nested/folder/file.json"));
    }

    @Test
    public void testQueryStringAndFragmentStripping() {
        assertEquals("index.html", sanitizer.sanitize("/index.html?query=test#hash"));
        assertEquals("assets/main.css", sanitizer.sanitize("/assets/main.css?v=2.1"));
    }

    @Test
    public void testDotSegmentCanonicalizationInsideRoot() {
        assertEquals("assets/style.css", sanitizer.sanitize("/assets/sub/../style.css"));
        assertEquals("app.js", sanitizer.sanitize("/sub/dir/../../app.js"));
        assertEquals("test/file.txt", sanitizer.sanitize("/test/./file.txt"));
    }

    @Test
    public void testDirectoryTraversalAttacksBlocked() {
        // Simple parent traversal
        assertNull(sanitizer.sanitize("../"));
        assertNull(sanitizer.sanitize("/../"));
        assertNull(sanitizer.sanitize("/../../etc/passwd"));
        assertNull(sanitizer.sanitize("sub/../../escape.txt"));

        // Encoded traversal
        assertNull(sanitizer.sanitize("/..%2F..%2Fetc/passwd"));
        assertNull(sanitizer.sanitize("/%2e%2e/"));
        assertNull(sanitizer.sanitize("/%2e%2e/test"));

        // Double-encoded traversal
        assertNull(sanitizer.sanitize("/%252e%252e/"));
        assertNull(sanitizer.sanitize("/%252e%252e%252fetc/passwd"));

        // Backslash traversal
        assertNull(sanitizer.sanitize("..\\"));
        assertNull(sanitizer.sanitize("\\..\\windows\\system32"));
        assertNull(sanitizer.sanitize("/sub\\..\\..\\escape"));
    }

    @Test
    public void testNullByteAttacksBlocked() {
        assertNull(sanitizer.sanitize("/index.html\0.jpg"));
        assertNull(sanitizer.sanitize("/assets%00/secret"));
    }

    @Test
    public void testDriveLetterAndAbsolutePathsBlocked() {
        assertNull(sanitizer.sanitize("C:/Windows/System32"));
        assertNull(sanitizer.sanitize("/D:/data"));
        assertNull(sanitizer.sanitize("/file:stream"));
    }

    @Test
    public void testMultiPassNestedEncodingBlocked() {
        // Triple encoded
        assertNull(sanitizer.sanitize("/%25252e%25252e%25252f"));
        assertNull(sanitizer.sanitize("/%25252e%25252e%25252fetc%25252fpasswd"));
        // Quad encoded
        assertNull(sanitizer.sanitize("/%2525252e%2525252e%2525252f"));
    }

    @Test
    public void testControlCharactersAndHeaderInjectionBlocked() {
        assertNull(sanitizer.sanitize("/index.html\r\nSet-Cookie:bad"));
        assertNull(sanitizer.sanitize("/index.html\n"));
        assertNull(sanitizer.sanitize("/index.html\r"));
        assertNull(sanitizer.sanitize("/index.html%0d%0a"));
        assertNull(sanitizer.sanitize("/assets/\tstyle.css"));
    }

    @Test
    public void testSensitiveMetadataAndDotfilesBlocked() {
        assertNull(sanitizer.sanitize("/.env"));
        assertNull(sanitizer.sanitize("/config/.env.local"));
        assertNull(sanitizer.sanitize("/.git/config"));
        assertNull(sanitizer.sanitize("/.gitignore"));
        assertNull(sanitizer.sanitize("/.svn/entries"));
        assertNull(sanitizer.sanitize("/.DS_Store"));
        assertNull(sanitizer.sanitize("/WEB-INF/web.xml"));
        assertNull(sanitizer.sanitize("/META-INF/MANIFEST.MF"));
    }

    @Test
    public void testDotAbuseBlocked() {
        assertNull(sanitizer.sanitize("/.../test"));
        assertNull(sanitizer.sanitize("/sub/.../file"));
    }
}

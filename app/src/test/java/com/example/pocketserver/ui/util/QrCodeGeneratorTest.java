package com.example.pocketserver.ui.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import org.junit.Test;

/**
 * Unit tests for QrCodeGenerator.
 */
public class QrCodeGeneratorTest {

    @Test
    public void testEncodeValidUrl() throws WriterException {
        String testUrl = "https://portfolio.pocketserver.dev";
        BitMatrix matrix = QrCodeGenerator.encodeBitMatrix(testUrl, 256);

        assertNotNull(matrix);
        assertEquals(256, matrix.getWidth());
        assertEquals(256, matrix.getHeight());

        // Ensure matrix contains both black and white modules
        boolean hasBlack = false;
        boolean hasWhite = false;
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                if (matrix.get(x, y)) {
                    hasBlack = true;
                } else {
                    hasWhite = true;
                }
            }
        }
        assertTrue("QR code must have black modules", hasBlack);
        assertTrue("QR code must have white modules", hasWhite);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEncodeEmptyStringThrows() throws WriterException {
        QrCodeGenerator.encodeBitMatrix("   ", 256);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEncodeInvalidSizeThrows() throws WriterException {
        QrCodeGenerator.encodeBitMatrix("https://example.com", 0);
    }
}

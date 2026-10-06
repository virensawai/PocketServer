package com.example.pocketserver.ui.util;

import android.graphics.Bitmap;
import android.graphics.Color;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class to generate high-resolution QR code Bitmaps using ZXing.
 * Used by {@link com.example.pocketserver.ui.qr.QrCodeBottomSheetDialogFragment}
 * for one-scan visitor access to live websites.
 */
public final class QrCodeGenerator {

    private QrCodeGenerator() {}

    /**
     * Generates a square QR Code Bitmap from the provided content string.
     *
     * @param content Text or URL to encode.
     * @param size    Width and height in pixels.
     * @return ARGB_8888 or RGB_565 Bitmap containing the QR matrix.
     * @throws WriterException If ZXing fails to encode the content.
     */
    @NonNull
    public static Bitmap generate(@NonNull String content, int size) throws WriterException {
        return generate(content, size, Color.BLACK, Color.WHITE);
    }

    @NonNull
    public static BitMatrix encodeBitMatrix(@NonNull String content, int size) throws WriterException {
        if (content.trim().isEmpty()) {
            throw new IllegalArgumentException("Content cannot be empty");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be greater than zero");
        }

        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 1);

        QRCodeWriter writer = new QRCodeWriter();
        return writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints);
    }

    /**
     * Generates a square QR Code Bitmap with customizable foreground and background colors.
     */
    @NonNull
    public static Bitmap generate(
            @NonNull String content,
            int size,
            @ColorInt int foregroundColor,
            @ColorInt int backgroundColor) throws WriterException {
        BitMatrix bitMatrix = encodeBitMatrix(content, size);

        int width = bitMatrix.getWidth();
        int height = bitMatrix.getHeight();
        int[] pixels = new int[width * height];

        for (int y = 0; y < height; y++) {
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                pixels[offset + x] = bitMatrix.get(x, y) ? foregroundColor : backgroundColor;
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }
}

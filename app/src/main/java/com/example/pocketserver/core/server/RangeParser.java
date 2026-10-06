package com.example.pocketserver.core.server;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Parses and validates HTTP RFC 7233 byte range request headers.
 */
public class RangeParser {

    /**
     * Represents a validated byte range slice within a file.
     */
    public static class ByteRange {
        private final long start;
        private final long end;
        private final long totalLength;

        public ByteRange(long start, long end, long totalLength) {
            this.start = start;
            this.end = end;
            this.totalLength = totalLength;
        }

        public long getStart() {
            return start;
        }

        public long getEnd() {
            return end;
        }

        public long getLength() {
            return (end - start) + 1;
        }

        public long getTotalLength() {
            return totalLength;
        }

        @NonNull
        public String toContentRangeHeader() {
            return "bytes " + start + "-" + end + "/" + totalLength;
        }
    }

    /**
     * Parses an HTTP "Range" header (e.g. "bytes=0-1024", "bytes=500-", "bytes=-200").
     *
     * @param rangeHeader value of the Range header
     * @param totalLength total size of the file in bytes
     * @return parsed ByteRange, or null if invalid or header not present
     * @throws RangeNotSatisfiableException if the range is syntax-valid but outside file bounds (HTTP 416)
     */
    @Nullable
    public static ByteRange parse(@Nullable String rangeHeader, long totalLength)
            throws RangeNotSatisfiableException {
        if (rangeHeader == null || !rangeHeader.trim().startsWith("bytes=")) {
            return null;
        }

        String spec = rangeHeader.trim().substring("bytes=".length()).trim();
        // Support only first range if multiple comma-separated ranges are supplied
        int commaIdx = spec.indexOf(',');
        if (commaIdx >= 0) {
            spec = spec.substring(0, commaIdx).trim();
        }

        int dashIdx = spec.indexOf('-');
        if (dashIdx < 0) {
            throw new RangeNotSatisfiableException(totalLength);
        }

        String startStr = spec.substring(0, dashIdx).trim();
        String endStr = spec.substring(dashIdx + 1).trim();

        long start;
        long end;

        try {
            if (startStr.isEmpty()) {
                // Suffix range: bytes=-500 (last 500 bytes)
                if (endStr.isEmpty()) {
                    throw new RangeNotSatisfiableException(totalLength);
                }
                long suffixLength = Long.parseLong(endStr);
                if (suffixLength <= 0) {
                    throw new RangeNotSatisfiableException(totalLength);
                }
                if (suffixLength >= totalLength) {
                    start = 0;
                } else {
                    start = totalLength - suffixLength;
                }
                end = totalLength - 1;
            } else {
                start = Long.parseLong(startStr);
                if (endStr.isEmpty()) {
                    // Open range: bytes=1000- (from 1000 to EOF)
                    end = totalLength - 1;
                } else {
                    // Explicit range: bytes=1000-2000
                    end = Long.parseLong(endStr);
                }
            }
        } catch (NumberFormatException e) {
            throw new RangeNotSatisfiableException(totalLength);
        }

        // Validate range bounds
        if (totalLength == 0 || start >= totalLength || start > end || start < 0) {
            throw new RangeNotSatisfiableException(totalLength);
        }

        // Clip end if beyond totalLength - 1
        if (end >= totalLength) {
            end = totalLength - 1;
        }

        return new ByteRange(start, end, totalLength);
    }

    /**
     * Thrown when requested byte range is outside file boundaries (triggers HTTP 416).
     */
    public static class RangeNotSatisfiableException extends Exception {
        private final long totalLength;

        public RangeNotSatisfiableException(long totalLength) {
            super("Requested range not satisfiable for file size " + totalLength);
            this.totalLength = totalLength;
        }

        public long getTotalLength() {
            return totalLength;
        }
    }
}

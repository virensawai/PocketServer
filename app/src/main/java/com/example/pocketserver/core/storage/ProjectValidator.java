package com.example.pocketserver.core.storage;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

/**
 * Validates selected project directories without full recursive crawling.
 */
public class ProjectValidator {

    public static class ValidationResult {
        private final boolean isValid;
        private final boolean hasIndexHtml;
        private final String projectName;
        private final int immediateItemCount;
        private final String errorMessage;

        public ValidationResult(boolean isValid,
                                boolean hasIndexHtml,
                                @NonNull String projectName,
                                int immediateItemCount,
                                @Nullable String errorMessage) {
            this.isValid = isValid;
            this.hasIndexHtml = hasIndexHtml;
            this.projectName = projectName;
            this.immediateItemCount = immediateItemCount;
            this.errorMessage = errorMessage;
        }

        public boolean isValid() {
            return isValid;
        }

        public boolean hasIndexHtml() {
            return hasIndexHtml;
        }

        @NonNull
        public String getProjectName() {
            return projectName;
        }

        public int getImmediateItemCount() {
            return immediateItemCount;
        }

        @Nullable
        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Performs lightweight validation of a selected SAF root directory tree.
     */
    @NonNull
    public static ValidationResult validate(@NonNull Context context, @NonNull Uri treeUri) {
        DocumentFile rootDoc;
        if ("file".equalsIgnoreCase(treeUri.getScheme()) && treeUri.getPath() != null) {
            rootDoc = DocumentFile.fromFile(new java.io.File(treeUri.getPath()));
        } else {
            rootDoc = DocumentFile.fromTreeUri(context, treeUri);
        }
        if (rootDoc == null || !rootDoc.exists() || !rootDoc.isDirectory()) {
            return new ValidationResult(false, false, "Unknown", 0, "Selected path is not an accessible directory.");
        }

        String name = rootDoc.getName();
        if (name == null || name.isEmpty()) {
            name = "website";
        }

        DocumentFile[] children = rootDoc.listFiles();
        int count = children.length;
        boolean hasIndex = false;

        for (DocumentFile child : children) {
            if ("index.html".equalsIgnoreCase(child.getName()) && child.isFile()) {
                hasIndex = true;
                break;
            }
        }

        if (!hasIndex) {
            return new ValidationResult(
                    false,
                    false,
                    name,
                    count,
                    "No index.html found. A static website project requires an index.html file in the root directory."
            );
        }

        return new ValidationResult(true, true, name, count, null);
    }
}

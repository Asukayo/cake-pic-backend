package com.sharkycake.proofing.upload;

import java.io.File;

/**
 * 已处理、待上传的预览文件及其真实元数据。
 */
public class PreparedPreview {

    private final File file;
    private final String displayName;
    private final long sizeBytes;
    private final String sha256;
    private final int width;
    private final int height;
    private final String contentType;

    public PreparedPreview(File file, String displayName, long sizeBytes, String sha256,
                           int width, int height, String contentType) {
        this.file = file;
        this.displayName = displayName;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.width = width;
        this.height = height;
        this.contentType = contentType;
    }

    public File getFile() {
        return file;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getContentType() {
        return contentType;
    }
}

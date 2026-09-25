package com.sharkycake.proofing.upload;

import cn.hutool.core.io.FileUtil;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.sharkycake.common.exception.BusinessException;
import com.sharkycake.common.exception.ErrorCode;
import com.sharkycake.common.exception.ThrowUtils;
import com.sharkycake.infrastructure.cos.ProofingStorageManager;
import com.sharkycake.proofing.auth.ProofingProjectAuthService;
import com.sharkycake.proofing.entity.ProofingAsset;
import com.sharkycake.proofing.vo.ProofingPreviewUploadVO;
import com.sharkycake.space.constant.SpaceUserPermissionConstant;
import com.sharkycake.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.IIOException;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.Locale;

/**
 * 单张预览上传的非事务编排入口。
 */
@Service
public class ProofingPreviewUploadService {

    private static final long MAX_SOURCE_BYTES = 20L * 1024 * 1024;
    private static final long MAX_PIXELS = 40_000_000L;
    private static final int MAX_LONG_EDGE = 1600;
    private static final long MAX_PREVIEW_BYTES = 2L * 1024 * 1024;
    private static final float[] JPEG_QUALITIES = {0.85f, 0.75f, 0.65f, 0.55f};

    private final ProofingProjectAuthService authService;
    private final ProofingPreviewTxService txService;
    private final ProofingStorageManager storageManager;

    public ProofingPreviewUploadService(ProofingProjectAuthService authService,
                                       ProofingPreviewTxService txService,
                                       ProofingStorageManager storageManager) {
        this.authService = authService;
        this.txService = txService;
        this.storageManager = storageManager;
    }

    /**
     * 向选单id上传对应的选单信息
     * 1. 先创建asset记录，
     * 2. 上传图片
     * 3.1 上传成功写入item
     * 3.2 更新asset记录和project的version
     * 4 COS 已上传而数据库关联失败时标记待删除；COS 结果不确定时等待 STAGING 到期
     */
    public ProofingPreviewUploadVO uploadPreview(Long projectId, MultipartFile file,
                                                Long expectedVersion, User loginUser) throws Exception {
        // 图片处理前做一次快速鉴权；短事务中仍要按数据库最新归属重新检查。
        authService.requireProjectPermission(projectId, loginUser,
                SpaceUserPermissionConstant.PROOFING_MANAGE);
        PreparedPreview preview = preparePreview(file);
        ProofingAsset asset = null;
        boolean uploaded = false;
        try {
            asset = txService.reservePreview(projectId, expectedVersion, loginUser, preview);
            // 第一笔事务已经提交，此处不能持有项目行锁。
            storageManager.putObject(asset.getObjectKey(), preview.getFile(), preview.getContentType());
            uploaded = true;
            return txService.completePreview(projectId, asset.getId(),
                    expectedVersion, loginUser, preview);
        } catch (Exception e) {
            if (asset != null && uploaded) {
                try {
                    // COS 已返回成功，可立即交给清理任务；请求异常时对象可能晚到，留在 STAGING 等到期扫描。
                    txService.markDeletePendingIfStaging(asset.getId());
                } catch (Exception cleanupError) {
                    e.addSuppressed(cleanupError);
                }
            }
            throw e;
        } finally {
            FileUtil.del(preview.getFile());
        }
    }

    /**
     * 将用户文件变成受控的 JPEG 预览。成功后由 uploadPreview 的 finally 删除临时文件；
     * 本方法中途失败则自行删除已创建的临时文件。
     */
    PreparedPreview preparePreview(MultipartFile file) throws Exception {
        ThrowUtils.throwIf(file == null || file.isEmpty() || file.getSize() > MAX_SOURCE_BYTES,
                ErrorCode.PARAMS_ERROR, "图片不能为空且不得超过 20 MiB");

        BufferedImage source;
        try (InputStream stream = file.getInputStream();
             ImageInputStream imageInput = ImageIO.createImageInputStream(stream)) {
            ThrowUtils.throwIf(imageInput == null, ErrorCode.PARAMS_ERROR, "无法读取图片");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            ThrowUtils.throwIf(!readers.hasNext(), ErrorCode.PARAMS_ERROR, "只支持 JPEG/PNG 图片");
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                // 文件名和请求 Content-Type 都可伪造；以实际解码器识别的格式为准。
                String format = reader.getFormatName().toUpperCase(Locale.ROOT);
                ThrowUtils.throwIf(!"JPEG".equals(format) && !"PNG".equals(format),
                        ErrorCode.PARAMS_ERROR, "只支持 JPEG/PNG 图片");
                // 先读头部尺寸，再完整解码，避免超大像素图片直接占满内存。
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                ThrowUtils.throwIf(width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS,
                        ErrorCode.PARAMS_ERROR, "图片像素不得超过 4000 万");
                source = reader.read(0);
                ThrowUtils.throwIf(source == null, ErrorCode.PARAMS_ERROR, "图片解码失败");
            } catch (IIOException e) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片内容损坏或无法解码");
            } finally {
                reader.dispose();
            }
        }

        // EXIF 方向只用来调整像素；输出文件重新编码，不复制原图的 EXIF/GPS。
        int orientation = readOrientation(file);
        boolean swapDimensions = orientation >= 5;
        int orientedWidth = swapDimensions ? source.getHeight() : source.getWidth();
        int orientedHeight = swapDimensions ? source.getWidth() : source.getHeight();
        double scale = Math.min(1.0, (double) MAX_LONG_EDGE / Math.max(orientedWidth, orientedHeight));
        int targetWidth = Math.max(1, (int) Math.round(orientedWidth * scale));
        int targetHeight = Math.max(1, (int) Math.round(orientedHeight * scale));

        // 统一输出 JPEG；透明 PNG 铺白底，避免透明区域变黑。
        BufferedImage preview = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = preview.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            AffineTransform transform = AffineTransform.getScaleInstance(
                    targetWidth / (double) orientedWidth, targetHeight / (double) orientedHeight);
            transform.concatenate(orientationTransform(orientation, source.getWidth(), source.getHeight()));
            graphics.drawImage(source, transform, null);
        } finally {
            graphics.dispose();
        }

        File output = null;
        try {
            output = Files.createTempFile("proofing-preview-", ".jpg").toFile();
            // 有界地降低 JPEG 质量；仍超限就拒绝，不能无限压缩。
            for (float quality : JPEG_QUALITIES) {
                writeJpeg(preview, output, quality);
                if (output.length() > 0 && output.length() <= MAX_PREVIEW_BYTES) {
                    return new PreparedPreview(output, displayName(file), output.length(),
                            sha256(output), targetWidth, targetHeight, "image/jpeg");
                }
            }
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "预览文件超过 2 MiB");
        } catch (Exception | Error e) {
            // 尚未交给上传编排时，临时文件由本方法负责清理。
            if (output != null) {
                try {
                    Files.deleteIfExists(output.toPath());
                } catch (Exception cleanupError) {
                    e.addSuppressed(cleanupError);
                }
            }
            throw e;
        }
    }

    /** 只读取 EXIF 朝向；无朝向标签时按正常方向处理。 */
    /** 只读取 EXIF 朝向；无朝向标签时按正常方向处理。 */
    private int readOrientation(MultipartFile file) throws Exception {
        try (InputStream stream = file.getInputStream()) {
            Metadata metadata = ImageMetadataReader.readMetadata(stream, file.getSize());
            ExifIFD0Directory exif = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            Integer orientation = exif == null ? null : exif.getInteger(ExifIFD0Directory.TAG_ORIENTATION);
            if (orientation == null) {
                return 1;
            }
            ThrowUtils.throwIf(orientation < 1 || orientation > 8,
                    ErrorCode.PARAMS_ERROR, "无效的图片朝向");
            return orientation;
        } catch (ImageProcessingException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无法读取图片元数据");
        }
    }

    /** EXIF 1～8 对应的翻转/旋转；变换后的宽高在调用方计算。 */
    private AffineTransform orientationTransform(int orientation, int width, int height) {
        switch (orientation) {
            case 2: return new AffineTransform(-1, 0, 0, 1, width, 0);
            case 3: return new AffineTransform(-1, 0, 0, -1, width, height);
            case 4: return new AffineTransform(1, 0, 0, -1, 0, height);
            case 5: return new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6: return new AffineTransform(0, 1, -1, 0, height, 0);
            case 7: return new AffineTransform(0, -1, -1, 0, height, width);
            case 8: return new AffineTransform(0, -1, 1, 0, 0, width);
            default: return new AffineTransform();
        }
    }

    /** 每次以截断模式重写同一个临时文件，避免较短的重试结果留下尾部旧字节。 */
    private void writeJpeg(BufferedImage image, File output, float quality) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        ThrowUtils.throwIf(!writers.hasNext(), ErrorCode.SYSTEM_ERROR, "缺少 JPEG 编码器");
        ImageWriter writer = writers.next();
        try (OutputStream fileOutput = Files.newOutputStream(output.toPath(),
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
             ImageOutputStream imageOutput = ImageIO.createImageOutputStream(fileOutput)) {
            ThrowUtils.throwIf(imageOutput == null, ErrorCode.SYSTEM_ERROR, "无法创建图片输出流");
            writer.setOutput(imageOutput);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(quality);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
    }

    /** 分块计算最终文件摘要，记录的是重编码后的内容。 */
    private String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream stream = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = stream.read(buffer)) != -1) {
                digest.update(buffer, 0, length);
            }
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) {
            hex.append(Character.forDigit((value >> 4) & 0xf, 16));
            hex.append(Character.forDigit(value & 0xf, 16));
        }
        return hex.toString();
    }

    private String displayName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.trim().isEmpty()) {
            return "preview.jpg";
        }
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty()) {
            return "preview.jpg";
        }
        int end = name.offsetByCodePoints(0, Math.min(128, name.codePointCount(0, name.length())));
        return name.substring(0, end);
    }
}

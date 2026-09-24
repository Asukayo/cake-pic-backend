package com.sharkycake.picture.dto.file;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

/**
 * 用于接受图片解析信息的包装类
 */
@Data
public class UploadPictureResult {

    /**
     * 图片地址
     */
    private String url;

    /**
     * 缩略图Url
     */
    private String thumbnailUrl;

    /**
     * 图片名称
     */
    private String picName;

    /**
     * 文件体积
     */
    private Long picSize;

    /**
     * 图片宽度
     */
    private int picWidth;

    /**
     * 图片高度
     */
    private int picHeight;

    /**
     * 图片宽高比
     */
    private Double picScale;

    /**
     * 图片格式
     */
    private String picFormat;

    /**
     * 图片主色调
     */
    private String picColor;


    private String originalKey;


    private String compressedKey;


    private String thumbnailKey;


    private String storageBucket;


}

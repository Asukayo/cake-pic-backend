package com.sharkycake.picture.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class PictureUploadRequest implements Serializable {

    /**
     * 图片id （用于修改）
     */
    private Long id;

    /**
     * 文件地址
     */
    private String fileUrl;

    /**
     * 图片名称
     */
    private String picName;

    /**
     * 图片分类；新图省略时使用“未分类”，替换时保留原分类
     */
    private String category;

    /**
     * 图片标签；省略时新图为空列表，替换时保留原标签
     */
    private List<String> tags;

    /**
     * spaceId字段,指定上传到哪个空间,为空则上传到公共空间
     */
    private Long spaceId;


}

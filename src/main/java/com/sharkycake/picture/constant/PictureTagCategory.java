package com.sharkycake.picture.constant;

import lombok.Data;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

/** 图片上传和筛选页面使用的推荐标签与分类。 */
@Data
public class PictureTagCategory implements Serializable {

    public static final String DEFAULT_CATEGORY = "未分类";

    // 摄影选项优先；旧选项暂留，方便筛选已有作品。
    List<String> tagList = Arrays.asList(
            "婚礼", "毕业照", "情侣", "亲子", "宠物", "旅行", "城市", "自然",
            "夜景", "黑白", "胶片", "航拍", "微距", "长曝光",
            "热门", "美少女", "搞笑", "meme", "艺术", "背景", "创意");

    List<String> categoryList = Arrays.asList(
            DEFAULT_CATEGORY, "人像摄影", "婚礼摄影", "风光摄影", "街头摄影",
            "纪实摄影", "建筑摄影", "静物摄影", "商业摄影", "动物摄影",
            "模板", "二次元", "表情包", "素材", "梗图");
}

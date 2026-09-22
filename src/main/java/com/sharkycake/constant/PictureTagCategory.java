package com.sharkycake.constant;

import lombok.Data;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

/**
 * 早期常用的标签与分类
 * 后期规模大起来可以使用数据表维护
 */
@Data
public class PictureTagCategory implements Serializable {

    List<String> tagList = Arrays.asList("热门","美少女","搞笑","meme","艺术","背景","创意");

    List<String> categoryList = Arrays.asList("模板","二次元","表情包","素材","梗图");
}

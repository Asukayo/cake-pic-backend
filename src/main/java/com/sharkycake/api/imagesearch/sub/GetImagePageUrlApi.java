package com.sharkycake.api.imagesearch.sub;

import cn.hutool.core.io.resource.BytesResource;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.sharkycake.exception.BusinessException;
import com.sharkycake.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class GetImagePageUrlApi {

    /**
     * 获取图片页面地址
     *
     * @param imageUrl
     * @return
     */
    public static String getImagePageUrl(String imageUrl) {

        // 💡 针对 WebP 格式的特殊处理：利用腾讯云 COS 万象实时转码为 JPG
        if (imageUrl != null && imageUrl.toLowerCase().contains(".webp")) {
            // 在原 URL 后追加腾讯云的数据处理参数 imageMogr2/format/jpg
            String separator = imageUrl.contains("?") ? "&" : "?";
            imageUrl = imageUrl + separator + "imageMogr2/format/jpg";
        }

        // 1. 先将网络图片下载到内存中（转为字节数组），自己把控图片的获取过程
        byte[] imageBytes = HttpUtil.downloadBytes(imageUrl);

        // 💡 新增：动态提取图片原始文件名（包含正确的后缀）
        String fileName = "image.jpg"; // 默认兜底名字
        try {
            // 获取 URL 最后面的文件名，例如 "2026-02-25_lMFrtw3aZk0BAkxI.webp"
            String path = new java.net.URL(imageUrl).getPath();
            fileName = path.substring(path.lastIndexOf('/') + 1);
            if (fileName.isEmpty() || !fileName.contains(".")) {
                fileName = "image.jpg";
            }
            // 🐛 核心修复：如果原本的后缀是 .webp，强制替换为 .jpg，骗过百度校验
            if (fileName.toLowerCase().endsWith(".webp")) {
                fileName = fileName.substring(0, fileName.lastIndexOf('.')) + ".jpg";
            }
        } catch (Exception e) {
            log.warn("解析图片文件名失败，使用默认文件名", e);
        }
        // 2. 准备请求参数
        Map<String, Object> formData = new HashMap<>();
        // 关键点：传入 BytesResource。Hutool 检测到 Resource 对象后，会自动将请求头切换为 multipart/form-data
        formData.put("image", new BytesResource(imageBytes, fileName));
        formData.put("tn", "pc");
        formData.put("from", "pc");
        // 关键点：既然变成了文件上传，百度的上传模式参数需要改为 PC_UPLOAD_SEARCH
        formData.put("image_source", "PC_UPLOAD_SEARCH");
        // 获取当前时间戳
        long uptime = System.currentTimeMillis();
        // 请求地址
        String url = "https://graph.baidu.com/upload?uptime=" + uptime;

        try {
            // 2. 发送 POST 请求到百度接口
            HttpResponse response = HttpRequest.post(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Referer", "https://graph.baidu.com/") // 加上 Referer 防反爬
                    .form(formData)
                    .timeout(10000) // 上传文件耗时略长，建议超时时间设为 10 秒
                    .execute();

            log.info("Response: {}", response.body()); // 务必打印出来看一眼

            // 判断响应状态
            if (HttpStatus.HTTP_OK != response.getStatus()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口调用失败");
            }
            // 解析响应
            String responseBody = response.body();
            log.info("百度接口返回原始数据: {}", responseBody); // 这里的输出会告诉你 status 到底是多少，错误信息是什么
            Map<String, Object> result = JSONUtil.toBean(responseBody, Map.class);

            // 3. 处理响应结果
            if (result == null || !Integer.valueOf(0).equals(result.get("status"))) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口调用失败");
            }
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            String rawUrl = (String) data.get("url");
            // 对 URL 进行解码
            String searchResultUrl = URLUtil.decode(rawUrl, StandardCharsets.UTF_8);
            // 如果 URL 为空
            if (searchResultUrl == null) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "未返回有效结果");
            }
            return searchResultUrl;
        } catch (Exception e) {
            log.error("搜索失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "搜索失败");
        }
    }

    public static void main(String[] args) {
        // 测试以图搜图功能
        String imageUrl = "https://www.codefather.cn/logo.png";
        String result = getImagePageUrl(imageUrl);
        System.out.println("搜索成功，结果 URL：" + result);
    }
}


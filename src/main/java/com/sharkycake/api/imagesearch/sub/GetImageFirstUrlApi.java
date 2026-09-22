package com.sharkycake.api.imagesearch.sub;

import com.sharkycake.exception.BusinessException;
import com.sharkycake.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Slf4j
public class GetImageFirstUrlApi {

    /**
     * 获取图片列表页面地址
     *
     * @param url
     * @return
     */
    public static String getImageFirstUrl(String url) {
        try {
            // 使用 Jsoup 获取 HTML 内容，并伪装成浏览器请求
            Document document = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    // 添加 Referer 应对可能的防盗链机制
                    .referrer("https://graph.baidu.com/")
                    .timeout(10000) // 同样建议稍微延长超时时间
                    .get();

            // 为了方便排错，建议在找不到内容时，能在控制台看到百度到底返回了什么页面
//            log.info("获取到的网页源码: {}", document.html());
             log.info("获取到的网页源码长度: {}", document.html().length());

            // 获取所有 <script> 标签
            Elements scriptElements = document.getElementsByTag("script");

            // 遍历找到包含 `firstUrl` 的脚本内容
            for (Element script : scriptElements) {
                String scriptContent = script.html();
                if (scriptContent.contains("\"firstUrl\"")) {
                    // 正则表达式提取 firstUrl 的值
                    Pattern pattern = Pattern.compile("\"firstUrl\"\\s*:\\s*\"(.*?)\"");
                    Matcher matcher = pattern.matcher(scriptContent);
                    if (matcher.find()) {
                        String firstUrl = matcher.group(1);
                        // 处理转义字符
                        firstUrl = firstUrl.replace("\\/", "/");
                        return firstUrl;
                    }
                }
            }

            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未找到 url");
        } catch (Exception e) {
            log.error("搜索失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "搜索失败");
        }
    }

    public static void main(String[] args) {
        // 1. 先调用第一个接口，上传图片并获取最新的、有效的百度搜索结果页 URL
        // 注意：确保 GetImagePageUrlApi 已经按照上一轮修改完毕
        String imageUrl = "https://www.codefather.cn/logo.png"; // 或者换一个更通用的图片链接
        System.out.println("正在上传图片至百度...");
        String baiduSearchPageUrl = GetImagePageUrlApi.getImagePageUrl(imageUrl);
        System.out.println("成功获取搜索结果页 URL：" + baiduSearchPageUrl);

        // 2. 将刚获取到的新鲜 URL 传给当前方法进行解析
        System.out.println("正在解析结果页获取第一张图...");
        String imageFirstUrl = getImageFirstUrl(baiduSearchPageUrl);
        System.out.println("搜索大功告成，首图结果 URL：" + imageFirstUrl);
    }
}


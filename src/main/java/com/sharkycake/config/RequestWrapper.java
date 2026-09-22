package com.sharkycake.config;

import lombok.extern.slf4j.Slf4j;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 包装请求，使 InputStream 可以重复读取
 *
 * @author pine
 */
@Slf4j
public class RequestWrapper extends HttpServletRequestWrapper {

    private final byte[] body;
    private final Charset charset;

    public RequestWrapper(HttpServletRequest request) {
        super(request);
        charset = request.getCharacterEncoding() == null ? StandardCharsets.UTF_8
                : Charset.forName(request.getCharacterEncoding());
        try (InputStream inputStream = request.getInputStream()) {
            body = inputStream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("读取请求体失败", e);
        }
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return byteArrayInputStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }

            @Override
            public int read() throws IOException {
                return byteArrayInputStream.read();
            }
        };

    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(this.getInputStream(), charset));
    }

    public String getBody() {
        return new String(body, charset);
    }

}

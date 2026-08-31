package com.chatdiet.alexa;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Reads a request's entire body into memory once, up front, so it can be read again downstream -
 * a servlet request's body is normally consumable only once, but Alexa's signature is computed
 * over the raw bytes ({@link AlexaSignatureFilter}) and the controller still needs to parse the
 * same bytes as JSON afterward.
 */
class CachedBodyHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    CachedBodyHttpServletRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        cachedBody = request.getInputStream().readAllBytes();
    }

    byte[] getCachedBody() {
        return cachedBody;
    }

    @Override
    public ServletInputStream getInputStream() {
        var byteStream = new ByteArrayInputStream(cachedBody);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return byteStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                throw new UnsupportedOperationException("Async reads are not needed here");
            }

            @Override
            public int read() {
                return byteStream.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
}

package com.cqnu.enums;

import org.apache.http.HttpRequest;
import org.apache.http.client.methods.*;

/**
 * 枚举支持的请求方式
 *
 * @Author: ZhangHao
 * @Date: 2020-02-23
 */
public enum HttpMethod {

    GET(HttpGet.class),
    POST(HttpPost.class),
    PUT(HttpPut.class),
    DELETE(HttpDelete.class);

    Class<? extends HttpRequestBase> requestMethod;

    HttpMethod(Class<? extends HttpRequestBase> requestMethod) {
        this.requestMethod = requestMethod;
    }

    public Class<? extends HttpRequestBase> getRequestMethod() {
        return requestMethod;
    }

    /**
     * 根据枚举的方法创建对应的HTTP请求对象
     *
     * @return 枚举对应的HTTP请求对象
     */
    public HttpRequestBase createMethod() {
        try {
            return this.requestMethod.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("反射创建请求对象失败.", e);
        }
    }
}

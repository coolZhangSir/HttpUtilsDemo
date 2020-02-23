package com.cqnu;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.cqnu.enums.HttpDownloadMethod;
import com.cqnu.enums.HttpMethod;
import com.cqnu.exception.HttpRequestException;
import com.sun.deploy.net.HttpUtils;
import org.apache.http.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 在HttpClient框架(V4.5.9)上的进一步封装, 具体功能如下:
 * 1. 对请求的常用结果类型的封装, 如StatusLine/String/JSONObject/JSONArray.
 * 2. 不再重载NameValuePairs的List类型参数支持,统一调整参数类型为JSONObject, 提供两种类型转换的静态方法.
 * 3. 支持get和post请求方式的下载.
 *
 * @author ZhangHao
 * @date 2019/2/23
 */
public class HttpClientUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientUtils.class);


    /**
     * 根据传入的请求执行后续操作, 返回响应对象, 自行选择后续处理方式.
     *
     * @param requestMethod 调用方创建的post请求
     * @param url 请求的地址
     * @param parameters 请求需要附加的实体内容集合, 可以调用{@link HttpClientUtils#convertNameValueList2Json(List)}
     * @param headers 请求头
     * @return 请求响应对象, 后续可以选择多种解析方式
     * @throws HttpRequestException post请求失败时, 抛出此异常
     */
    private static CloseableHttpResponse invokeRequest(HttpRequestBase requestMethod,
                                                       CloseableHttpClient httpClient,
                                                       String url,
                                                       JSONObject parameters,
                                                       Header... headers) throws HttpRequestException {
        final URI uri;
        boolean isGetRequest = requestMethod instanceof HttpGet;
        if (isGetRequest) {
            uri = buildURI(url, parameters);
        } else {
            uri = buildURI(url);
        }
        requestMethod.setURI(uri);

        setHeader(requestMethod, headers);
        setConfig(requestMethod);

        if (!isGetRequest) {
            setEntity((HttpEntityEnclosingRequestBase) requestMethod, parameters);
        }

        return executeRequest(requestMethod, httpClient);
    }

    /**
     * 支持Get/Post方式下载, 将请求返回的响应流写入传入的OutputStream对象中
     *
     * @param httpMethod 请求方式, 由枚举类{@link HttpDownloadMethod}限定.
     * @param url 请求的地址
     * @param parameters 请求需要附加的实体内容集合, 可以调用{@link HttpClientUtils#convertNameValueList2Json(List)}
     * @param outputStream 用于下载的输出流, 响应流将会写入到该对象中.
     * @param headers 请求头
     * @throws HttpRequestException post请求失败时, 抛出此异常
     */
    public static void download(HttpDownloadMethod httpMethod,
                                String url,
                                JSONObject parameters,
                                OutputStream outputStream,
                                Header... headers) throws HttpRequestException {

        HttpRequestBase requestMethod = httpMethod.createMethod();
        try (
                CloseableHttpClient httpClient = HttpClients.createDefault();
                CloseableHttpResponse response = invokeRequest(requestMethod, httpClient, url, parameters, headers);
                InputStream inputStream = handleResponseForInputStream(response);
        ) {
            if (response.getStatusLine().getStatusCode() >= 400) {
                String errorMessage = inputStream2String(inputStream);
            }
            byte[] buffer = new byte[1024];
            int length = 0;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
        } catch (IOException e) {
            LOGGER.error("清理HTTP请求失败", e);
        } finally {
            requestMethod.releaseConnection();
        }
    }

    /**
     * 根据指定的请求方式和其他信息完成请求操作并将响应流转换为字符串并返回.
     * <p><b>默认编码UTF-8. </b></p>
     *
     * @param httpMethod 请求方式, 由由枚举类{@link HttpMethod}限定.
     * @param url 请求的地址
     * @param parameters 请求需要附加的实体内容集合
     * @param headers 请求头
     * @return 字符串类型请求响应内容
     * @throws HttpRequestException 请求失败时, 抛出此异常
     */
    public String requestForString(HttpMethod httpMethod, String url, JSONObject parameters, Header... headers) throws HttpRequestException {
        HttpRequestBase requestMethod = httpMethod.createMethod();
        try (
                CloseableHttpClient httpClient = HttpClients.createDefault();
                CloseableHttpResponse response = invokeRequest(requestMethod, httpClient, url, parameters, headers);
                InputStream inputStream = handleResponseForInputStream(response);
        ) {
            return inputStream2String(inputStream);
        } catch (IOException e) {
            LOGGER.error("清理HTTP请求失败", e);
        } finally {
            requestMethod.releaseConnection();
        }

        return null;
    }

    /**
     *  在{@link HttpClientUtils#requestForString(String, List, Header...)}
     *  的基础上将结果封装为JSONObject.
     *
     * @param parameters 请求参数集合
     * @param headers 请求头
     * @return JsonArray类型的响应结果
     * @see HttpClientUtils#requestForString(String, List, Header...)
     */
    public JSONObject requestForJSONObject(HttpMethod httpMethod, String url, JSONObject parameters, Header... headers) throws HttpRequestException {
        String responseContent = requestForString(httpMethod, url, parameters, headers);
        try {
            return JSONObject.parseObject(responseContent);
        } catch (JSONException e) {
            LOGGER.error("json转换失败, text={}", responseContent);
            throw e;
        }
    }

    /**
     *  在{@link HttpClientUtils#requestForString(HttpMethod, String, JSONObject, Header...)}
     *  的基础上将结果封装为JSONArray.
     *
     * @param parameters 请求参数集合
     * @param headers 请求头
     * @return JsonArray类型的响应结果
     * @see HttpClientUtils#requestForString(HttpMethod, String, JSONObject, Header...)
     */
    public JSONArray requestForJSONArray(HttpMethod httpMethod,
                                         String url,
                                         JSONObject parameters,
                                         Header... headers) throws HttpRequestException {
        String responseContent = requestForString(httpMethod, url, parameters, headers);
        try {
            return JSON.parseArray(responseContent);
        } catch (JSONException e) {
            LOGGER.error("json数组转换失败, text={}", responseContent);
            throw e;
        }
    }

    /**
     * 根据指定的请求方式和其他信息完成请求操作并将请求的状态信息返回.
     *
     * @param httpMethod 请求方式, 由由枚举类{@link HttpMethod}限定.
     * @param url 请求的地址
     * @param parameters 请求需要附加的实体内容集合
     * @param headers 请求头
     * @return 请求返回的状态信息, 包含状态码等.
     * @throws HttpRequestException 请求失败时, 抛出此异常
     */
    public StatusLine requestForStatusLine(HttpMethod httpMethod,
                                           String url,
                                           JSONObject parameters,
                                           Header... headers) throws HttpRequestException {
        HttpRequestBase requestMethod = httpMethod.createMethod();
        try (
                CloseableHttpClient httpClient = HttpClients.createDefault();
                CloseableHttpResponse response = invokeRequest(requestMethod, httpClient, url, parameters, headers);
        ) {
            return response.getStatusLine();
        } catch (IOException e) {
            LOGGER.error("HTTP请求关闭失败", e);
        } finally {
            requestMethod.releaseConnection();
        }

        requestMethod.releaseConnection();
        return null;
    }

    /**
     * 重载以简化参数, 此方法parameters默认为null.
     *
     * @see #buildURI(String, List)
     */
    private static URI buildURI(String uri) throws HttpRequestException {
        // 为了让编译器明确具体调用的重载方法, 进行强转.
        return buildURI(uri, (List<NameValuePair>) null);
    }

    /**
     * 重载JSONObject参数类型.
     *
     * @see #buildURI(String, List)
     */
    private static URI buildURI(String uri, JSONObject parameters) throws HttpRequestException {
        return buildURI(uri, convertJson2NameValueList(parameters));
    }

    /**
     * 根据传入的字符串url和参数构造uri
     *
     * @param uri        目标url
     * @param parameters GET/DELETE等请求所需要附加的参数
     * @return 构造好的完整uri
     * @throws HttpRequestException 当解析构造完成之后的uri失败时, 抛出此异常
     */
    private static URI buildURI(String uri, List<NameValuePair> parameters) throws HttpRequestException {
        try {
            final URIBuilder uriBuilder = new URIBuilder(URI.create(uri));
            if (parameters != null && !parameters.isEmpty()) {
                uriBuilder.addParameters(parameters);
            }
            return uriBuilder.build();
        } catch (URISyntaxException e) {
            String exceptionMessage = "URI构造失败. uri=" + uri;
            throw new HttpRequestException(exceptionMessage, HttpStatus.SC_INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * 添加指定的请求头
     *
     * @param headers 可选参数, 如果不传则不进行后续添加操作
     */
    private static void setHeader(HttpRequestBase request, Header... headers) {
        if (headers.length > 0) {
            request.setHeaders(headers);
        }
    }

    /**
     * 设置请求的相关配置项, 暂不对外扩展开放, 填充默认值.
     *
     * @param request 请求对象
     */
    private static void setConfig(HttpRequestBase request) {
        // 设置超时时间
        RequestConfig config = RequestConfig.custom()
                .setConnectionRequestTimeout(1000)
                .setSocketTimeout(1000)
                .setConnectTimeout(1000)
                .build();
        request.setConfig(config);
    }

    /**
     * 设置请求实体
     *
     * @param request post/put请求对象
     * @param parameters  请求需要附加的实体内容集合, 接收JSONObject/List<NameValuePair>格式的参数.
     *                根据不同的参数类型会附加不同的Content-type头.
     *                如果是JSONObject会附加(), 如果是List<NameValuePair>会附加()
     * @throws HttpRequestException http encode请求实体内容失败时, 抛出此异常
     */
    @SuppressWarnings("unchecked")
    private static void setEntity(HttpEntityEnclosingRequestBase request, Object parameters) throws HttpRequestException {
        if (parameters instanceof JSONObject) {
            request.setHeader("Content-type", "application/json");
            StringEntity stringEntity = new StringEntity(((JSONObject) parameters).toJSONString(), StandardCharsets.UTF_8);
            request.setEntity(stringEntity);
        } else if (parameters instanceof List) {
            request.setHeader("Content-type", "application/x-www-form-urlencoded");
            try {
                UrlEncodedFormEntity encodedFormEntity = new UrlEncodedFormEntity((List<? extends NameValuePair>) parameters);
                request.setEntity(encodedFormEntity);
            } catch (UnsupportedEncodingException e) {
                String exceptionMessage = "http encode请求实体内容失败";
                throw new HttpRequestException(exceptionMessage, HttpStatus.SC_BAD_REQUEST, e);
            }
        }
    }

    /**
     * 执行目录请求
     *
     * @param request 构造好uri, header, 实体等内容的请求
     * @param client  执行请求的客户端
     * @return 请求响应对象
     * @throws HttpRequestException 当连接到目标uri失败时, 抛出此异常
     */
    private static CloseableHttpResponse executeRequest(HttpRequestBase request, CloseableHttpClient client) throws HttpRequestException {
        CloseableHttpResponse response;
        try {
            response = client.execute(request);
        } catch (IOException e) {
            String exceptionMessage = "链接目标uri失败, 请检查网络环境. uri" + request.getURI().toString();
            throw new HttpRequestException(exceptionMessage, HttpStatus.SC_INTERNAL_SERVER_ERROR, e);
        }
        return response;
    }

    /**
     * 处理并返回响应的内容
     *
     * @param response 响应的内容
     * @return 处理请求响应中所携带的内容
     * @throws HttpRequestException 当获取响应流失败时,抛出此异常,
     */
    private static InputStream handleResponseForInputStream(CloseableHttpResponse response) throws HttpRequestException {
        HttpEntity entity = response.getEntity();
        try {
            return entity.getContent();
        } catch (IOException e) {
            throw new HttpRequestException("获取响应流失败",
                    HttpStatus.SC_INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * 将返回的输入流转换为String
     *
     * @param inputStream 带有数据的输入流,可以是来自http请求的响应或者是文件流等
     * @return 返回从输入流转换出来String
     */
    public static String inputStream2String(InputStream inputStream) throws HttpRequestException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int length = 0;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
            return outputStream.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new HttpRequestException("读取流中的文本内容失败.",HttpStatus.SC_INTERNAL_SERVER_ERROR , e);
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                LOGGER.error("响应流关闭失败.", e);
            }
        }
    }

    /**
     * 将{@link com.alibaba.fastjson.JSONObject}转换为List&lt;NameValuePair&gt;
     * @param jsonObject Json参数
     * @return 包含所有参数的List
     */
    public static List<NameValuePair> convertJson2NameValueList(JSONObject jsonObject){
        List<NameValuePair> list = new ArrayList<>();
        if (jsonObject != null && !jsonObject.isEmpty()) {
            for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
                list.add(new BasicNameValuePair(entry.getKey(), entry.getValue().toString()));
            }
        }
        return list;
    }

    /**
     * 将List&lt;NameValuePair&gt;转换为{@link com.alibaba.fastjson.JSONObject}
     * @param list 请求参数
     * @return 包含所有参数的JSONObject
     */
    public static JSONObject convertNameValueList2Json(List<NameValuePair> list){
        JSONObject jsonObject = new JSONObject();
        if (list != null && !list.isEmpty()) {
            for (NameValuePair pair : list) {
                jsonObject.put(pair.getName(), pair.getValue());
            }
        }
        return jsonObject;
    }

}

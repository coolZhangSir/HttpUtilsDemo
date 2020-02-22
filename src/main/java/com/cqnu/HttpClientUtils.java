package com.cqnu;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cqnu.exception.HttpRequestException;
import org.apache.http.*;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 在HttpClient框架(V4.5.9)上的进一步封装, 具体功能如下:
 * 1. 对请求的常用结果类型的封装, 如StatusCode/String/JSONArray/JSONArray/InputStream.
 * 2. Post/Put请求参数类型支持List&lt;NameValuePair&gt;/JSONObject类型.
 *    Get/Delete请求参数通常不会用到复杂类型, 所以暂时只支持List&lt;NameValuePair&gt;类型.
 * 3. 除了ForInputStream方法, 其余请求方法都支持自动释放请求连接.(v1.0新增)
 *
 * @date 2019/9/19
 */
public class HttpClientUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientUtils.class);

    /**
     * 根据传入的Post请求执行后续操作, 返回响应对象, 自行选择后续处理方式.
     * 该方法内部不创建请求对象, 因为即使创建了也没有合适的方法返回给调用者, 不便于后续释放连接的操作.
     *
     * @param post 调用方创建的post请求
     * @param url 请求的地址
     * @param params 请求需要附加的实体内容集合, 接收JSONObject/List&lt;NameValuePair&gt;格式的参数
     * @param headers 请求头
     * @return 请求响应对象, 后续可以选择多种解析方式
     * @throws HttpRequestException post请求失败时, 抛出此异常
     */
    private static CloseableHttpResponse invokePost(HttpPost post, String url, Object params, Header... headers) throws HttpRequestException {
        final URI uri = buildURI(url);
        post.setURI(uri);

        setHeader(post, headers);
        setEntity(post, params);

        return executeRequest(post);
    }

    /**
     * 创建Post请求, 根据其他信息完成请求操作并返回响应状态码.
     * <p><b>该方法内部创建连接并负责释放连接.</b></p>
     *
     * @param params 请求需要附加的实体内容集合
     * @param headers 请求头
     * @throws HttpRequestException post请求失败时, 抛出此异常
     */
    public static Integer requestPostForStatusCode(String url, JSONObject params, Header... headers) throws HttpRequestException {
        HttpPost post = new HttpPost();
        CloseableHttpResponse response = invokePost(post, url, params, headers);
        Integer statusCode = handleResponseForStatusCode(response);

        post.releaseConnection();

        return statusCode;
    }

    /**
     * 创建Post请求, 根据其他信息完成请求操作并返回响应状态码.
     * <p><b>该方法内部创建连接并负责释放连接.</b></p>
     *
     * @param entityList 请求需要附加的实体内容集合
     * @param headers 请求头
     * @throws HttpRequestException post请求失败时, 抛出此异常
     */
    public static Integer requestPostForStatusCode(String url, List<NameValuePair> entityList, Header... headers) throws HttpRequestException {
        HttpPost post = new HttpPost();
        CloseableHttpResponse response = invokePost(post, url, entityList, headers);
        Integer statusCode = handleResponseForStatusCode(response);

        post.releaseConnection();

        return statusCode;
    }



    /**
     * 根据传入Post请求的其他信息完成请求操作并返回响应流
     * <p>由于直接将响应流返回给调用者, 该方法没有释放连接的时机,所以该方法内部不创建连接也不负责释放连接.</p>
     * <p><b>由调用者负责手动释放连接.</b></p>
     * <p><code>post.releaseConnection()</code></p>
     *
     * @param post 由调用方传入的请求对象, 方便调用者在处理完该响应流后释放该请求连接.
     * @param params 请求需要附加的实体内容集合
     * @param headers 请求头
     * @return post请求的响应流
     * @throws HttpRequestException post请求失败时, 抛出此异常
     */
    public static InputStream requestPostForInputStream(HttpPost post, String url, JSONObject params, Header... headers) throws HttpRequestException {
        CloseableHttpResponse response = invokePost(post, url, params, headers);
        return handleResponseForInputStream(response);
    }

    /**
     * 根据传入请求相关信息完成请求操作并返回响应流
     * <p><b>该方法会创建Post请求, 由于直接将响应流返回给调用者, 该方法没有释放连接的时机, 所以该方法不会释放连接.</b></p>
     *
     * @param params 请求需要附加的实体内容集合
     * @param headers 请求头
     * @return post请求的响应流
     * @throws HttpRequestException post请求失败时, 抛出此异常
     */
    public static InputStream requestPostForInputStream(String url, JSONObject params, Header... headers) throws HttpRequestException {
        CloseableHttpResponse response = invokePost(new HttpPost(), url, params, headers);
        return handleResponseForInputStream(response);
    }

    /**
     * 根据传入Post请求的其他信息完成请求操作并返回响应流
     * <p>由于直接将响应流返回给调用者, 该方法没有释放连接的时机,所以该方法内部不创建连接也不负责释放连接.</p>
     * <p><b>由调用者负责手动释放连接.</b></p>
     * <p><code>post.releaseConnection()</code></p>
     *
     * @param post 请求对象, 由调用方传入, 方便处理完该流后释放该请求连接.
     * @param entityList 请求需要附加的实体内容集合
     * @param headers 请求头
     * @return post请求的响应流
     * @throws HttpRequestException post请求失败时, 抛出此异常
     */
    public static InputStream requestPostForInputStream(HttpPost post, String url, List<NameValuePair> entityList, Header... headers) throws HttpRequestException {
        CloseableHttpResponse response = invokePost(post, url, entityList, headers);
        return handleResponseForInputStream(response);
    }

    /**
     * 根据传入请求相关信息完成请求操作并返回响应流
     * <p><b>该方法会创建Post请求, 由于直接将响应流返回给调用者, 该方法没有释放连接的时机, 所以该方法不会释放连接.</b></p>
     *
     * @param entityList 请求需要附加的实体内容集合
     * @param headers 请求头
     * @return post请求的响应流
     * @throws HttpRequestException post请求失败时, 抛出此异常
     */
    public static InputStream requestPostForInputStream(String url, List<NameValuePair> entityList, Header... headers) throws HttpRequestException {
        HttpPost post = new HttpPost();
        CloseableHttpResponse response = invokePost(post, url, entityList, headers);
        return handleResponseForInputStream(response);
    }

    /**
     * 创建Post请求, 根据其他信息完成请求操作并将响应流转换为字符串并返回.
     * <p><b>默认编码UTF-8. 该方法内部创建连接并负责释放连接.</b></p>
     *
     * @param params 请求需要附加的实体内容集合
     * @param headers 请求头
     * @return post请求的文本内容
     * @throws HttpRequestException post请求失败时, 抛出此异常
     */
    public static String requestPostForString(String url, JSONObject params, Header... headers)
            throws HttpRequestException {
        HttpPost post = new HttpPost();
        InputStream inputStream = requestPostForInputStream(post, url, params, headers);
        String responseBody = inputStream2String(inputStream);

        post.releaseConnection();

        return responseBody;
    }

    /**
     * 创建Post请求的其他信息完成请求操作并将响应流转换为字符串并返回.
     * <p><b>默认编码UTF-8. 该方法内部创建连接并负责释放连接.</b></p>
     *
     * @param entityList 请求需要附加的实体内容集合
     * @param headers 请求头
     * @return post请求的文本内容
     * @throws HttpRequestException post请求失败时, 抛出此异常
     */
    public static String requestPostForString(String url, List<NameValuePair> entityList, Header... headers)
            throws HttpRequestException {
        HttpPost post = new HttpPost();
        InputStream inputStream = requestPostForInputStream(post, url, entityList, headers);
        String responseBody = inputStream2String(inputStream);

        post.releaseConnection();

        return responseBody;
    }

    /**
     * 根据传入的Get请求执行后续操作, 返回响应对象, 自行选择后续处理方式.
     * 该方法内部不创建请求对象, 因为即使创建了也不好返回, 不便于后续释放连接的操作.
     *
     * @param get 调用方创建的get请求
     * @param parameters 请求参数集合
     * @param headers 请求头
     * @return 响应对象
     */
    private static CloseableHttpResponse invokeGet(HttpGet get, String url,
                                                   List<NameValuePair> parameters,
                                                   Header... headers) throws HttpRequestException {
        final URI uri = buildURI(url, parameters);
        get.setURI(uri);

        get.setHeaders(headers);

        return executeRequest(get);
    }

    /**
     * 根据传入Get请求的其他信息完成请求操作并返回响应流.
     * <p><b>
     *     该方法的参数都是追加到url后面, 不会包含太复杂的参数类型, 所以不提供JSONObject类的重载方法.
     *     如果需要将某JSONObject格式的结果作为参数传递,
     *     该类中提供了{@link HttpClientUtils#convertJson2NameValueList(com.alibaba.fastjson.JSONObject)}方法用于转换.
     * </b></p>
     * <p>由于直接将响应流返回给调用者, 该方法没有释放连接的时机,所以该方法内部不创建连接也不负责释放连接.</p>
     * <p><b>由调用者负责手动释放连接.</b></p>
     * <p><code>get.releaseConnection()</code></p>
     *
     * @param get 由调用方传入的请求对象, 方便调用者在处理完该响应流后释放该请求连接.
     * @param parameters 用于构建uri的请求参数集合
     * @param headers 请求头
     * @return 响应流
     */
    public static InputStream requestGetForInputStream(HttpGet get, String url, List<NameValuePair> parameters, Header... headers) throws HttpRequestException {
        CloseableHttpResponse response = invokeGet(get, url, parameters, headers);
        return handleResponseForInputStream(response);
    }

    /**
     * 根据传入请求相关信息完成请求操作并返回响应流
     * <p><b>该方法会创建Post请求, 由于直接将响应流返回给调用者, 该方法没有释放连接的时机, 所以该方法不会释放连接.</b></p>
     *
     * @param parameters 用于构建uri的请求参数集合
     * @param headers 请求头
     * @return 响应实体
     * @throws HttpRequestException get请求出错时, 抛出此异常
     */
    public static InputStream requestGetForInputStream(String url, List<NameValuePair> parameters, Header... headers) throws HttpRequestException {
        HttpGet get = new HttpGet();
        CloseableHttpResponse response = invokeGet(get, url, parameters, headers);
        return handleResponseForInputStream(response);
    }

    /**
     * 创建Get请求, 根据其他信息完成请求操作并将响应流转换为字符串并返回.
     * <p><b>默认编码UTF-8. 该方法内部创建连接并负责释放连接.</b></p>
     *
     * @param parameters 请求参数集合
     * @param headers 请求头
     * @return get请求响应的文本内容
     */
    public static String requestGetForString(String url, List<NameValuePair> parameters, Header... headers) throws HttpRequestException {
        HttpGet get = new HttpGet();
        InputStream inputStream = HttpClientUtils.requestGetForInputStream(get, url, parameters);
        String responseBody = inputStream2String(inputStream);

        get.releaseConnection();

        return responseBody;

    }

    /**
     *  在{@link HttpClientUtils#requestGetForString(String, List, Header...)}
     *  的基础上将结果封装为JSONArray.
     *  <p><b>该方法内部调用时创建连接并负责释放连接.</b></p>
     *
     * @param parameters 请求参数集合
     * @param headers 请求头
     * @return JsonArray类型的响应实体
     * @see HttpClientUtils#requestGetForString(String, List, Header...)
     */
    public static JSONArray requestGetForJsonArray(String url, List<NameValuePair> parameters, Header... headers) throws HttpRequestException {
        String text = requestGetForString(url, parameters, headers);
        return JSON.parseArray(text);
    }

    /**
     * 根据传入的Put请求执行后续操作, 返回响应对象, 自行选择后续处理方式.
     * 该方法内部不创建请求对象, 因为即使创建了也没有合适的方法返回给调用者, 不便于后续释放连接的操作.
     *
     * @param put 调用方创建的put请求
     * @param url 请求的地址
     * @param params 请求需要附加的实体内容集合, 接收JSONObject/List&lt;NameValuePair&gt;格式的参数
     * @param headers 请求头
     * @return 请求响应对象, 后续可以选择多种解析方式
     * @throws HttpRequestException put请求失败时, 抛出此异常
     */
    private static CloseableHttpResponse invokePut(HttpPut put, String url, Object params, Header... headers) throws HttpRequestException {
        final URI uri = buildURI(url);
        put.setURI(uri);

        setHeader(put, headers);
        setEntity(put, params);

        return executeRequest(put);
    }

    /**
     * 创建Put请求, 根据其他信息完成请求操作并返回响应状态码.
     * <p><b>该方法内部创建连接并负责释放连接.</b></p>
     *
     * @param params 请求需要附加的实体内容集合
     * @param headers 请求头
     * @throws HttpRequestException put请求失败时, 抛出此异常
     */
    public static Integer requestPutForStatusCode(String url, JSONObject params, Header... headers) throws HttpRequestException {
        HttpPut put = new HttpPut();
        CloseableHttpResponse response = invokePut(put, url, params, headers);
        Integer statusCode = handleResponseForStatusCode(response);

        put.releaseConnection();

        return statusCode;
    }

    /**
     * 创建Put请求, 根据其他信息完成请求操作并返回响应状态码.
     * <p><b>该方法内部创建连接并负责释放连接.</b></p>
     *
     * @param entityList 请求需要附加的实体内容集合
     * @param headers 请求头
     * @throws HttpRequestException put请求失败时, 抛出此异常
     */
    public static Integer requestPutForStatusCode(String url, List<NameValuePair> entityList, Header... headers) throws HttpRequestException {
        HttpPut put = new HttpPut();
        CloseableHttpResponse response = invokePut(put, url, entityList, headers);
        Integer statusCode = handleResponseForStatusCode(response);

        put.releaseConnection();

        return statusCode;
    }



    /**
     * 根据传入Put请求的其他信息完成请求操作并返回响应流
     * <p>由于直接将响应流返回给调用者, 该方法没有释放连接的时机,所以该方法内部不创建连接也不负责释放连接.</p>
     * <p><b>由调用者负责手动释放连接.</b></p>
     * <p><code>put.releaseConnection()</code></p>
     *
     * @param put 由调用方传入的请求对象, 方便调用者在处理完该响应流后释放该请求连接.
     * @param params 请求需要附加的实体内容集合
     * @param headers 请求头
     * @return put请求的响应流
     * @throws HttpRequestException put请求失败时, 抛出此异常
     */
    public static InputStream requestPutForInputStream(HttpPut put, String url, JSONObject params, Header... headers) throws HttpRequestException {
        CloseableHttpResponse response = invokePut(put, url, params, headers);
        return handleResponseForInputStream(response);
    }

    /**
     * 根据传入请求相关信息完成请求操作并返回响应流
     * <p><b>该方法会创建Put请求, 由于直接将响应流返回给调用者, 该方法没有释放连接的时机, 所以该方法不会释放连接.</b></p>
     *
     * @param params 请求需要附加的实体内容集合
     * @param headers 请求头
     * @return put请求的响应流
     * @throws HttpRequestException put请求失败时, 抛出此异常
     */
    public static InputStream requestPutForInputStream(String url, JSONObject params, Header... headers) throws HttpRequestException {
        CloseableHttpResponse response = invokePut(new HttpPut(), url, params, headers);
        return handleResponseForInputStream(response);
    }

    /**
     * 根据传入Put请求的其他信息完成请求操作并返回响应流
     * <p>由于直接将响应流返回给调用者, 该方法没有释放连接的时机,所以该方法内部不创建连接也不负责释放连接.</p>
     * <p><b>由调用者负责手动释放连接.</b></p>
     * <p><code>put.releaseConnection()</code></p>
     *
     * @param put 请求对象, 由调用方传入, 方便处理完该流后释放该请求连接.
     * @param entityList 请求需要附加的实体内容集合
     * @param headers 请求头
     * @return put请求的响应流
     * @throws HttpRequestException put请求失败时, 抛出此异常
     */
    public static InputStream requestPutForInputStream(HttpPut put, String url, List<NameValuePair> entityList, Header... headers) throws HttpRequestException {
        CloseableHttpResponse response = invokePut(put, url, entityList, headers);
        return handleResponseForInputStream(response);
    }

    /**
     * 根据传入请求相关信息完成请求操作并返回响应流
     * <p><b>该方法会创建Put请求, 由于直接将响应流返回给调用者, 该方法没有释放连接的时机, 所以该方法不会释放连接.</b></p>
     *
     * @param entityList 请求需要附加的实体内容集合
     * @param headers 请求头
     * @return put请求的响应流
     * @throws HttpRequestException put请求失败时, 抛出此异常
     */
    public static InputStream requestPutForInputStream(String url, List<NameValuePair> entityList, Header... headers) throws HttpRequestException {
        HttpPut put = new HttpPut();
        CloseableHttpResponse response = invokePut(put, url, entityList, headers);
        return handleResponseForInputStream(response);
    }

    /**
     * 创建Put请求, 根据其他信息完成请求操作并将响应流转换为字符串并返回.
     * <p><b>默认编码UTF-8. 该方法内部创建连接并负责释放连接.</b></p>
     *
     * @param params 请求需要附加的实体内容集合
     * @param headers 请求头
     * @return put请求的文本内容
     * @throws HttpRequestException put请求失败时, 抛出此异常
     */
    public static String requestPutForString(String url, JSONObject params, Header... headers)
            throws HttpRequestException {
        HttpPut put = new HttpPut();
        InputStream inputStream = requestPutForInputStream(put, url, params, headers);
        String responseBody = inputStream2String(inputStream);

        put.releaseConnection();

        return responseBody;
    }

    /**
     * 创建Put请求的其他信息完成请求操作并将响应流转换为字符串并返回.
     * <p><b>默认编码UTF-8. 该方法内部创建连接并负责释放连接.</b></p>
     *
     * @param entityList 请求需要附加的实体内容集合
     * @param headers 请求头
     * @return put请求的文本内容
     * @throws HttpRequestException put请求失败时, 抛出此异常
     */
    public static String requestPutForString(String url, List<NameValuePair> entityList, Header... headers)
            throws HttpRequestException {
        HttpPut put = new HttpPut();
        InputStream inputStream = requestPutForInputStream(put, url, entityList, headers);
        String responseBody = inputStream2String(inputStream);

        put.releaseConnection();

        return responseBody;
    }

    /**
     * 根据传入的Delete请求执行后续操作, 返回响应对象, 自行选择后续处理方式.
     * 该方法内部不创建请求对象, 因为即使创建了也不好返回, 不便于后续释放连接的操作.
     *
     * @param delete 调用方创建的delete请求
     * @param parameters 请求参数集合
     * @param headers 请求头
     * @return 响应对象
     */
    private static CloseableHttpResponse invokeDelete(HttpDelete delete, String url,
                                                      List<NameValuePair> parameters,
                                                      Header... headers) throws HttpRequestException {
        final URI uri = buildURI(url, parameters);
        delete.setURI(uri);

        delete.setHeaders(headers);

        return executeRequest(delete);
    }

    /**
     * 根据传入Delete请求的其他信息完成请求操作并返回响应流.
     * <p>由于直接将响应流返回给调用者, 该方法没有释放连接的时机,所以该方法内部不创建连接也不负责释放连接.</p>
     * <p><b>由调用者负责手动释放连接.</b></p>
     * <p><code>delete.releaseConnection()</code></p>
     *
     * @param delete 由调用方传入的请求对象, 方便调用者在处理完该响应流后释放该请求连接.
     * @param parameters 用于构建uri的请求参数集合
     * @param headers 请求头
     * @return 响应流
     */
    private static InputStream requestDeleteForInputStream(HttpDelete delete, String url, List<NameValuePair> parameters, Header... headers) throws HttpRequestException {
        CloseableHttpResponse response = invokeDelete(delete, url, parameters, headers);
        return handleResponseForInputStream(response);
    }

    /**
     * 创建Delete请求, 根据其他信息完成请求操作并将响应流转换为字符串并返回.
     * <p><b>
     *      该方法的参数都是追加到url后面, 不会包含太复杂的参数类型, 所以不提供JSONObject类的重载方法.
     *      如果需要将某JSONObject格式的结果作为参数传递,
     *      该类中提供了{@link HttpClientUtils#convertJson2NameValueList(com.alibaba.fastjson.JSONObject)}方法用于转换.
     *  </b></p>
     * <p><b>默认编码UTF-8. 该方法内部创建连接并负责释放连接.</b></p>
     *
     * @param parameters 请求参数集合
     * @param headers 请求头
     * @return delete请求响应的文本内容
     */
    public static String requestDeleteForString(String url, List<NameValuePair> parameters, Header... headers) throws HttpRequestException {
        HttpDelete delete = new HttpDelete();
        InputStream inputStream = HttpClientUtils.requestDeleteForInputStream(delete, url, parameters);
        String responseBody = inputStream2String(inputStream);

        delete.releaseConnection();

        return responseBody;

    }

    /**
     * 重载以简化参数, 此方法parameters默认为null.
     * @see #buildURI(String, List)
     */
    private static URI buildURI(String uri) throws HttpRequestException {
        return buildURI(uri, null);
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
            String exceptionMessage = "请求URI解析失败,请求参数有误, 请检查. uri=" + uri;
            throw new HttpRequestException(exceptionMessage, HttpStatus.SC_INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * 添加指定的请求头
     * @param headers 可选参数, 如果不传则不进行后续添加操作
     */
    private static void setHeader(HttpEntityEnclosingRequest request, Header... headers){
        if (headers.length > 0) {
            request.setHeaders(headers);
        }
    }

    /**
     * 设置请求实体
     * @param request post/put请求对象
     * @param params 请求需要附加的实体内容集合, 接收JSONObject/List<NameValuePair>格式的参数.
     *               根据不同的参数类型会附加不同的Content-type头.
     *               如果是JSONObject会附加(), 如果是List<NameValuePair>会附加()
     * @throws HttpRequestException http encode请求实体内容失败时, 抛出此异常
     */
    @SuppressWarnings("unchecked")
    private static void setEntity(HttpEntityEnclosingRequest request, Object params) throws HttpRequestException {
        if (params instanceof JSONObject) {
            request.setHeader("Content-type", "application/json");
            StringEntity stringEntity = new StringEntity(((JSONObject)params).toJSONString(), StandardCharsets.UTF_8);
            request.setEntity(stringEntity);
        } else if (params instanceof List){
            request.setHeader("Content-type", "application/x-www-form-urlencoded");
            try {
                UrlEncodedFormEntity encodedFormEntity = new UrlEncodedFormEntity((List<? extends NameValuePair>) params);
                request.setEntity(encodedFormEntity);
            } catch (UnsupportedEncodingException e) {
                String exceptionMessage = "http encode请求实体内容失败";
                throw new HttpRequestException(exceptionMessage, HttpStatus.SC_BAD_REQUEST, e);
            }
        }
    }

    /**
     * 执行目录请求
     * @param request 构造好uri, header, 实体等内容的请求
     * @return 请求响应对象
     * @throws HttpRequestException 当连接到目标uri失败时, 抛出此异常
     */
    private static CloseableHttpResponse executeRequest(HttpRequestBase request) throws HttpRequestException {
        CloseableHttpResponse response;
        CloseableHttpClient client = HttpClients.createDefault();
        try {
            response = client.execute(request);
        } catch (IOException e) {
            String exceptionMessage = "链接目标uri失败, 请检查网络环境. uri" + request.getURI().toString();
            throw new HttpRequestException(exceptionMessage, HttpStatus.SC_INTERNAL_SERVER_ERROR, e);
        }
        return response;
    }

    /**
     * 处理响应,并返回响应状态码
     *
     * @param response 响应的内容
     * @return 响应状态码
     */
    private static Integer handleResponseForStatusCode(CloseableHttpResponse response) {
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode >= HttpStatus.SC_BAD_REQUEST) {
            LOGGER.error("请求连接出错,返回的状态码 = {}", statusCode);
        }
        return statusCode;
    }

    /**
     * 处理响应,并返回响应实体
     *
     * @param response 响应的内容
     * @return 响应实体
     * @throws HttpRequestException 当请求出错,响应状态码大于400时,抛出此异常,
     */
    private static HttpEntity handleResponseForHttpEntity(CloseableHttpResponse response) throws HttpRequestException {
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode >= HttpStatus.SC_BAD_REQUEST) {
            String exceptionMessage = "请求连接出错.";
            try {
                String cause = inputStream2String(response.getEntity().getContent());
                exceptionMessage += cause;
            } catch (Exception e) {
                exceptionMessage += "无法获取返回的出错原因.";
            }
            throw new HttpRequestException(exceptionMessage, statusCode);
        }

        return response.getEntity();
    }

    /**
     * 处理并返回响应的内容
     *
     * @param response 响应的内容
     * @return 处理请求响应中所携带的内容
     * @throws HttpRequestException 当请求出错,响应状态码大于400时,抛出此异常,
     */
    private static InputStream handleResponseForInputStream(CloseableHttpResponse response) throws HttpRequestException {
        HttpEntity entity = handleResponseForHttpEntity(response);
        try {
            return entity.getContent();
        } catch (IOException e) {
            throw new HttpRequestException("获取请求响应中的流失败",
                    HttpStatus.SC_INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * 将返回的输入流转换为String
     *
     * @param inputStream 带有数据的输入流,可以是来自http请求的响应或者是文件流等
     * @return 返回从输入流转换出来String
     */
    private static String inputStream2String(InputStream inputStream) throws HttpRequestException {
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
                e.printStackTrace();
            }
        }
    }

    /**
     * 将返回的输入流转换为JSONObject
     *
     * @param inputStream 带有数据的输入流,可以是来自http请求的响应或者是文件流等
     * @return 返回从输入流转换出来的jsonObject
     */
    private static JSONObject inputStream2JsonObject(InputStream inputStream) throws HttpRequestException {
        String text = inputStream2String(inputStream);
        return JSONObject.parseObject(text);
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
}

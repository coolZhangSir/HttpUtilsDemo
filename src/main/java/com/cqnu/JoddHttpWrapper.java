package com.cqnu;

import static com.cqnu.utill.Slf4jStyleFormatter.format;
import static jodd.net.MimeTypes.MIME_APPLICATION_JSON;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONValidator;
import com.cqnu.constant.CommonConstant;
import com.cqnu.exception.HttpRequestException;
import com.cqnu.utill.Assert;
import com.cqnu.utill.CollectionUtils;
import com.cqnu.utill.HttpStatus;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Objects;
import jodd.http.HttpException;
import jodd.http.HttpRequest;
import jodd.http.HttpResponse;
import jodd.net.MimeTypes;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * http请求包装类，在jodd-http上进行扩展，提供响应内容的相关校验功能。
 *
 * @author 山崎
 * @date 2021/5/11
 */
@Data
public class JoddHttpWrapper {

    /**
     * 包装的请求对象
     */
    private HttpRequest httpRequest;

    /**
     * 本次请求的响应对象
     */
    private HttpResponse httpResponse;

    /**
     * http请求的配置类
     */
    private JoddHttpConfig joddHttpConfig;

    /**
     * json响应体中的code校验结果。
     * 如果code未校验成功则跳过data和list的校验
     */
    private boolean codeValidateResult;

    /**
     * 请求响应体中的json, 不能直接访问该字段，应该通过{@link JoddHttpWrapper#getResponseJsonObject()}获取该字段
     */
    private JSONObject responseJsonObject;

    private JoddHttpWrapper(HttpRequest httpRequest, JoddHttpConfig joddHttpConfig) {
        this.joddHttpConfig = joddHttpConfig;
        this.httpRequest = httpRequest;

        configRequest();
    }

    /**
     * 根据传入的config对象配置request对象
     */
    private void configRequest() {
        httpRequest.connectionTimeout(joddHttpConfig.getTimeout());
        httpRequest.timeout(joddHttpConfig.getTimeout());
        httpRequest.contentType(joddHttpConfig.getMediaType(), joddHttpConfig.getCharset());
        httpRequest.accept(joddHttpConfig.getAccept());
    }

    /**
     * 根据指定HttpRequest参数创建包装类实例，会调用{@link JoddHttpConfig#getDefaultConfig()}获取默认的配置。
     *
     * @param httpRequest 请求参数，不能为null
     * @return 以默认配置创建的包装类
     * @throws IllegalArgumentException 当httpRequest为null时，抛出此异常
     */
    public static JoddHttpWrapper newInstance(HttpRequest httpRequest) {
        return newInstance(httpRequest, JoddHttpConfig.getDefaultConfig());
    }


    /**
     * 根据指定HttpRequest参数及JoddHttpConfig配置对象创建包装类实例。
     *
     * @param httpRequest    请求参数，不能为null
     * @param joddHttpConfig 请求配置对象，如果为null则启用默认配置
     * @return 以指定配置对象创建的包装类
     * @throws IllegalArgumentException 当httpRequest为null时，抛出此异常
     */
    public static JoddHttpWrapper newInstance(HttpRequest httpRequest, JoddHttpConfig joddHttpConfig) {
        Assert.notNull(httpRequest, "httpRequest不能为null");
        if (joddHttpConfig == null) {
            joddHttpConfig = JoddHttpConfig.getDefaultConfig();
        }
        return new JoddHttpWrapper(httpRequest, joddHttpConfig);
    }

    /**
     * 发送httpRequest
     *
     * @throws HttpRequestException 当http请求连接失败时，抛出该异常
     */
    public void sendRequest() {
        try {
            this.httpResponse = httpRequest.send();
        } catch (HttpException e) {
            String message = format("请求连接失败.url：[{}]", httpRequest.url());
            throw new HttpRequestException(message, e);
        }
    }

    /**
     * 对响应结果进行校验
     */
    public void validateResponse() {
        if (joddHttpConfig.isIgnoreAllValidation()) {
            return;
        }

        validateStatusCode();
        validateResponseContentType();
        validateJsonFormat();
        validateBusinessCode();
        validateDataJsonNodeExist();
        validateListJsonNodeExist();
    }

    /**
     * 从给定的json对象中取出business code
     *
     * @return json中包含的business code
     */
    public Integer getBusinessCode() {
        return getResponseJsonObject().getInteger(CommonConstant.JSON_KEY_BUSINESS_CODE);
    }

    /**
     * 获取请求响应体中的字符串转换过来的json对象，调用该方法会自动关闭http连接。
     *
     * @return 响应体中转换出来的json对象
     * @throws UnsupportedOperationException 当content-type响应头中mediaType非application/json时，抛出此异常
     */
    public JSONObject getResponseJsonObject() {
        if (isResponseMediaTypeNotJson()) {
            throw new UnsupportedOperationException("content-type响应头中mediaType非application/json，无法获取json对象. 请检查http请求和响应内容.");
        }

        if (responseJsonObject == null) {
            try {
                responseJsonObject = JSONObject.parseObject(httpResponse.bodyText());
            } finally {
                if (httpResponse != null) {
                    httpResponse.close();
                }
            }
        }

        return responseJsonObject;
    }

    /**
     * 获取请求响应体对应的字节数组，下载的文件一般都不大，所以jodd直接转成bytes问题不大，调用该方法会自动关闭http连接。
     *
     * @return 响应体对应的字节数组，当响应体body为空时，返回null
     */
    public byte[] getResponseBodyBytes() {
        try {
            return httpResponse.bodyBytes();
        } finally {
            httpResponse.close();
        }
    }

    /**
     * 根据响应的content-type头判断，是否为非application/json类型的响应体
     *
     * @return 是否为非application/json类型的响应体
     */
    private boolean isResponseMediaTypeNotJson() {
        return !(httpResponse.mediaType().contains(MIME_APPLICATION_JSON));
    }

    /**
     * 校验http响应体json中的data节点是否存在，满足以下任一条件都会跳过此校验。
     * <ol>
     *     <li> code校验失败或未校验
     *     <li> data未开启校验
     *     <li> list未开启校验
     *
     * @throws HttpRequestException http返回的json格式错误时，抛出此异常
     */
    public void validateListJsonNodeExist() {
        if (!codeValidateResult
            || !joddHttpConfig.isValidateDataJsonNodeExist()
            || !joddHttpConfig.isValidateListJsonNodeExist()) {
            return;
        }

        JSONObject responseJsonObject = getResponseJsonObject();
        JSONObject dataJsonObject = responseJsonObject.getJSONObject(CommonConstant.JSON_KEY_DATA);
        JSONArray listJsonArray = dataJsonObject.getJSONArray(CommonConstant.JSON_KEY_LIST);
        if (CollectionUtils.isEmpty(listJsonArray)) {
            String errorMessage = format("json响应体中的list节点不存在. url: [{}], statusCode: [{}], responseBodyText: [{}]",
                httpRequest.url(), httpResponse.statusCode(), httpResponse.bodyText());
            throw new HttpRequestException(errorMessage);
        }
    }

    /**
     * 校验http响应体json中的data节点是否存在，满足以下任一条件都会跳过此校验。
     * <ol>
     *     <li> code校验失败或未校验
     *     <li> data未开启校验
     *
     * @throws HttpRequestException http返回的json格式错误时，抛出此异常
     */
    public void validateDataJsonNodeExist() {
        if (!codeValidateResult || !joddHttpConfig.isValidateDataJsonNodeExist()) {
            return;
        }

        JSONObject responseJsonObject = getResponseJsonObject();
        JSONObject data = responseJsonObject.getJSONObject(CommonConstant.JSON_KEY_DATA);
        if (CollectionUtils.isEmpty(data)) {
            String errorMessage = format("json响应体中的data节点不存在. url: [{}], statusCode: [{}], responseBodyText: [{}]",
                httpRequest.url(), httpResponse.statusCode(), httpResponse.bodyText());
            throw new HttpRequestException(errorMessage);
        }
    }

    /**
     * 校验http响应体json中的code是否正确。
     * 如果未开启code校验则会跳过该校验
     *
     * @throws HttpRequestException http返回的json格式错误时，抛出此异常
     */
    public void validateBusinessCode() {
        if (!joddHttpConfig.isValidateCodeJsonNodeExist) {
            codeValidateResult = false;
            return;
        }

        Integer code = getBusinessCode();

        boolean isCodeMissing = Objects.isNull(code);
        boolean isBusinessCodeError = !Objects.equals(joddHttpConfig.getBusinessCode(), code);
        if (isCodeMissing || isBusinessCodeError) {
            String errorMessage = format("json响应体中的code不存在或者错误. url: [{}], statusCode: [{}], businessCode: [{}], responseBodyText: [{}]",
                httpRequest.url(), httpResponse.statusCode(), code, httpResponse.bodyText());
            throw new HttpRequestException(errorMessage);
        }

        codeValidateResult = true;
    }

    /**
     * 当响应头contentType为application/json类型时，校验响应体的json格式是否正确
     *
     * @throws HttpRequestException http返回的json格式错误时，抛出此异常
     */
    public void validateJsonFormat() {
        if (isResponseMediaTypeNotJson()) {
            return;
        }

        boolean isValidJson = JSONValidator.from(httpResponse.bodyText()).validate();
        if (!isValidJson) {
            String errorMessage = format("请求响应的Json串格式错误. url: [{}], statusCode: [{}], contentType: [{}], responseBodyText: [{}]",
                httpRequest.url(), httpResponse.statusCode(), httpResponse.contentType(), httpResponse.bodyText());
            throw new HttpRequestException(errorMessage);
        }
    }

    /**
     * 校验响应头中的contentType。
     * 当请求头设置的accept为application/json时，校验响应头的mediaType和charset是否与请求头中指定的匹配
     *
     * @throws HttpRequestException 当请求头设置的mediaType为application/json时，如果校验响应头的mediaType和charset与请求头中指定的不匹配，则抛出此异常
     */
    public void validateResponseContentType() {
        if (isAcceptNotJson()) {
            return;
        }

        boolean isMediaTypeMismatch = !Objects.equals(httpResponse.mediaType(), MIME_APPLICATION_JSON);
        boolean isCharsetMismatch = !Objects.equals(httpResponse.charset(), joddHttpConfig.getCharset());

        if (isMediaTypeMismatch || isCharsetMismatch) {
            String cause = null;
            if (isMediaTypeMismatch) {
                cause = "请求响应头content-type中的mediaType与请求头accept中指定的不匹配.";
            }

            if (isCharsetMismatch) {
                cause = "请求响应头content-type中的charset部分与请求头accept中指定的不匹配.";
            }

            String errorMessage = format(
                "{} url: [{}], statusCode: [{}], accept: [{}],contentType: [{}], responseBodyText: [{}]",
                cause, httpRequest.url(), httpResponse.statusCode(),
                joddHttpConfig.getAccept(), httpResponse.contentType(), httpResponse.bodyText());
            throw new HttpRequestException(errorMessage);
        }
    }

    /**
     * 校验http状态码是否成功。 当http状态码为2xx时，视为成功
     *
     * @throws HttpRequestException 当http状态码不为2xx开头时，抛出此异常
     */
    public void validateStatusCode() {
        if (!HttpStatus.valueOf(httpResponse.statusCode()).is2xxSuccessful()) {
            String errorMessage = format("请求响应的statusCode错误. url: [{}], statusCode: [{}], responseBodyText: [{}]",
                httpRequest.url(), httpResponse.statusCode(), httpResponse.bodyText());
            throw new HttpRequestException(errorMessage);
        }
    }

    /**
     * 根据请求的accept头判断，是否未指定接收application/json类型的响应体
     *
     * @return 是否未接收json类型的响应体
     */
    private boolean isAcceptNotJson() {
        String requestAccept = joddHttpConfig.getAccept();
        return !(requestAccept.contains(MIME_APPLICATION_JSON));
    }

    /**
     * jodd http 配置类
     * <p>
     * 不能直接创建该对象，只能调用{@link JoddHttpConfig#newConfigInstanceByDefault()}创建带有默认配置的对象。
     * 如果需要修改配置，请调用该对象相应的setter方法。
     */
    @Data
    @Accessors(chain = true)
    public static class JoddHttpConfig {

        /**
         * 请求处理成功时，响应体中携带的业务编码code默认值
         */
        public static final int DEFAULT_BUSINESS_CODE = 0;

        /**
         * 请求处理成功时，响应体中携带的code值，默认为{@link JoddHttpConfig#DEFAULT_BUSINESS_CODE}
         */
        private int businessCode;

        /**
         * ContentType中的mediaType，常量定义参考{@link MimeTypes}
         */
        private String mediaType;

        /**
         * 请求头ContentType中的charset，常量定义参考{@link StandardCharsets}
         */
        private String charset;

        /**
         * 响应头Accept，常量定义参考{@link StandardCharsets}
         */
        private String accept;

        /**
         * timeout，请求超时时间
         */
        private int timeout;

        /**
         * 是否需要校验响应体json中的data结点是否存在。
         * 当code错误时，一般不会有data节点（更不会有list），如果关闭code节点的校验，会自动关闭data和list节点的校验
         */
        private boolean isValidateCodeJsonNodeExist;

        /**
         * 是否需要校验响应体json中的data结点是否存在。
         */
        private boolean isValidateDataJsonNodeExist;

        /**
         * 是否需要校验响应体json中的list结点是否存在。
         * 由于list在data节点下，所以开启list节点校验时会自动开启data结点的校验
         */
        private boolean isValidateListJsonNodeExist;

        /**
         * 是否忽略所有校验规则。
         */
        private boolean isIgnoreAllValidation;

        /**
         * 默认的jodd-http配置对象
         */
        private static JoddHttpConfig DEFAULT_JODD_HTTP_CONFIG;

        /**
         * 默认的http请求超时时间
         */
        private static final int DEFAULT_TIMEOUT = 5000;

        private JoddHttpConfig() {
        }

        public JoddHttpConfig setCharset(String charset) {
            if (Charset.isSupported(charset)) {
                this.charset = charset;
                return this;
            }

            throw new UnsupportedCharsetException(charset);
        }

        public JoddHttpConfig setValidateCodeJsonNodeExist(boolean validateCodeJsonNodeExist) {
            this.isValidateCodeJsonNodeExist = validateCodeJsonNodeExist;
            if (!isValidateCodeJsonNodeExist) {
                this.isValidateDataJsonNodeExist = false;
            }
            return this;
        }

        public JoddHttpConfig setValidateListJsonNodeExist(boolean validateListJsonNodeExist) {
            this.isValidateListJsonNodeExist = validateListJsonNodeExist;
            if (this.isValidateListJsonNodeExist) {
                this.isValidateDataJsonNodeExist = true;
            }
            return this;
        }

        /**
         * 获取默认的jodd http 常量配置对象。
         * 默认配置参考：{@link JoddHttpConfig#newConfigInstanceByDefault()}
         *
         * @return 默认的jodd http 常量配置对象
         */
        private static JoddHttpConfig getDefaultConfig() {
            if (DEFAULT_JODD_HTTP_CONFIG == null) {
                DEFAULT_JODD_HTTP_CONFIG = newConfigInstanceByDefault();
            }
            return DEFAULT_JODD_HTTP_CONFIG;
        }

        /**
         * 创建带有默认值的的jodd http config对象，默认值如下:
         * <ol>
         *  <li> 响应头accept默认为application/json
         *  <li> 请求头contentType中的mediaType默认为application/json
         *  <li> 请求头contentType中的charset默认为utf-8
         *  <li> 响应体json中code成功的值默认为0
         *  <li> 请求超时时间默认为5秒
         *  <li> 开启响应体json中code节点是否存在和成功的校验
         *  <li> 开启响应体json中data节点是否存在的校验
         *  <p> 如果要处理特殊错误码，需要手动关闭响应体json中code节点是否存在和成功的校验：
         * {@code JoddHttpConfig.setValidateCodeJsonNodeExist(false)}
         *
         * @return 默认的jodd http config对象
         */
        public static JoddHttpConfig newConfigInstanceByDefault() {
            return new JoddHttpConfig()
                .setAccept(MIME_APPLICATION_JSON)
                .setMediaType(MIME_APPLICATION_JSON)
                .setCharset(StandardCharsets.UTF_8.name())
                .setTimeout(DEFAULT_TIMEOUT)
                .setBusinessCode(DEFAULT_BUSINESS_CODE)
                .setValidateCodeJsonNodeExist(true)
                .setValidateDataJsonNodeExist(true)
                ;
        }

        /**
         * 调用{@link JoddHttpConfig#newConfigInstanceByDefault()}创建带默认值的配置对象，并修改如下配置再返回：
         * <ol>
         *  <li> 响应头accept默认为application/octet-stream
         *  <li> 关闭响应体json中code节点是否存在的校验
         *  <li> 关闭响应体json中data节点是否存在的校验
         *
         * @return 默认的jodd http config对象
         */
        public static JoddHttpConfig newStreamConfigInstance() {
            return newConfigInstanceByDefault()
                .setAccept(MimeTypes.MIME_APPLICATION_OCTET_STREAM)
                .setValidateCodeJsonNodeExist(false)
                .setValidateDataJsonNodeExist(false)
                ;
        }

        /**
         * 调用{@link JoddHttpConfig#newConfigInstanceByDefault()}创建带默认值的配置对象，并修改如下配置再返回：
         * <ol>
         *  <li> 开启忽略所有校验
         *
         * @return 默认的jodd http config对象
         */
        public static JoddHttpConfig newNoValidationConfigInstance() {
            return newConfigInstanceByDefault()
                .setIgnoreAllValidation(true)
                ;
        }
    }
}

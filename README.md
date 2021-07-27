# 简介

在Jodd-Http框架(V5.1.3)上对请求结果进行封装, 支持以下类型:
* bytes
* JSONObject

# 支持的功能

1. 封装了bytes数组的下载.
2. 支持对请求结果进行业务逻辑校验
3. 通过配置类对请求和响应进行定制

# 说明
为了代码不报错，部分类是直接从spring框架中复制出来的。

# 快速开始

```java
import com.cqnu.JoddHttpWrapper.JoddHttpConfig;

public class Test {

    public static void main(String[] args) {
        String url = "https://www.baidu.com";

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("key", "value");

        HttpRequest httpRequest =
            HttpRequest.put(url)
                       .bodyText(jsonObject.toJSONString(),
                                 MimeTypes.MIME_APPLICATION_JSON,
                                 StandardCharsets.UTF_8.name());

        JSONObject jsonResponse = sendRequestForJsonObject(
            httpRequest,
            JoddHttpConfig.newNoValidationConfigInstance());
    }

    /**
     * 根据指定的配置和参数发送Http请求，并获取响应的jsonObject
     *
     * @param httpRequest    回调请求, 不能为null
     * @param joddHttpConfig http请求配置对象，如果为null则获取默认配置
     * @return 返回本次请求响应体转换而来的json对象
     * @throws HttpRequestException   当回调接口失败时，抛出此异常
     * @throws IllegalArgumentException 任一参数为null或者String类型的参数为空字符串时，抛出此异常
     */
    private static JSONObject sendRequestForJsonObject(HttpRequest httpRequest, JoddHttpConfig joddHttpConfig) {
        JoddHttpWrapper joddHttpWrapper = JoddHttpWrapper.newInstance(httpRequest, joddHttpConfig);

        try {
            joddHttpWrapper.sendRequest();
            joddHttpWrapper.validateResponse();
        } catch (WebofficeHttpException e) {
            throw new HttpRequestException("REQUEST_FAILED", e);
        }

        return joddHttpWrapper.getResponseJsonObject();
    }
}
```

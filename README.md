# 简介
在HttpClient框架上做的请求结果封装.
1. 对HTTP请求操作的响应结果进行常用类型的封装, 如StatusCode/String/JSONArray/JSONArray/InputStream.
2. Post/Put请求参数类型支持List<NameValuePair>/JSONObject类型.
   Get/Delete请求参数通常不会用到复杂类型, 所以暂时只支持List<NameValuePair>类型.
3. 除了ForInputStream方法, 其余请求方法都支持自动释放请求连接.(v1.0.0新增)

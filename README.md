# 简介
在HttpClient框架(V4.5.9)上对请求结果进行封装, 支持以下类型:
* StatusLine
* String
* JSONObject
* JSONArray

# 支持的功能
1. 支持get和post请求方式的下载.

# 下一步完善的功能
1. 添加配置类的支持
   
   由于没有参数类的封装, 很多配置都不支持指定, 
   纯粹的方法调用参数类型除了重载没有很好的限定方式.
   
   v1.0尝试重载了List<NameValuePair>和JSONObject参数类型, 写起来太麻烦了, 
   啥功能没有就几百行了, 看上去太麻瓜了.v2.0只支持JSONObject, 
   提供JSONObject和List<NameValuePair>类型之间互相转换的静态方法.
   后面再添加配置类应该就好解决了.

package com.cqnu.utill;

import org.slf4j.helpers.MessageFormatter;

/**
 * Slf4j风格的字符串格式化工具类
 *
 * @author 山崎
 * @date 2021/5/11
 */
public abstract class Slf4jStyleFormatter {

    /**
     * 调用slf4j的消息格式化器，支持占位符替换，简化字符串的拼接
     *
     * @param pattern 带占位符的字符串，不能为null
     * @param params 用于替换占位符的参数列表
     * @return 替换掉占位符的字符串
     */
    public static String format(String pattern, Object... params){
        Assert.notNull(pattern, "pattern不能为null");

        return MessageFormatter.arrayFormat(pattern, params).getMessage();
    }

}

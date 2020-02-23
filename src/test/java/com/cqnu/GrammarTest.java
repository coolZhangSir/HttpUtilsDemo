package com.cqnu;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author: ZhangHao
 * @Date: 2020-02-23
 */
public class GrammarTest {

    @Test
    public void testClass() {
        HttpRequestBase method = new HttpGet();
        System.out.println(method instanceof HttpGet);
        System.out.println(method.getClass().isAssignableFrom(HttpGet.class));
        System.out.println(method.getClass().isAssignableFrom(HttpRequestBase.class));
    }
}

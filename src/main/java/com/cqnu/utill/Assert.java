package com.cqnu.utill;

import com.sun.istack.internal.Nullable;

/**
 * 从Spring框架中复制的源代码
 *
 * @author 山崎
 * @date 2021/7/27
 */
public class Assert {

    /**
     * Assert that an object is not {@code null}.
     * <pre class="code">Assert.notNull(clazz, "The class must not be null");</pre>
     * @param object the object to check
     * @param message the exception message to use if the assertion fails
     * @throws IllegalArgumentException if the object is {@code null}
     */
    public static void notNull(@Nullable Object object, String message) {
        if (object == null) {
            throw new IllegalArgumentException(message);
        }
    }
}

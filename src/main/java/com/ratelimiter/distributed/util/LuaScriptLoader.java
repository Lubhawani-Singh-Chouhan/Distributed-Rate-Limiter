package com.ratelimiter.distributed.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

/**
 * Small helper for reading a Lua script off the classpath once at startup so it can be
 * registered as a {@link org.springframework.data.redis.core.script.RedisScript} bean.
 */
public final class LuaScriptLoader {

    private LuaScriptLoader() {
    }

    public static String load(String classpathLocation) {
        try (InputStream inputStream = new ClassPathResource(classpathLocation).getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load Lua script from " + classpathLocation, e);
        }
    }
}

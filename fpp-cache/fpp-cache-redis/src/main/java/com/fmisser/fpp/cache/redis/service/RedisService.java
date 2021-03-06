package com.fmisser.fpp.cache.redis.service;

import org.springframework.data.util.Pair;

import java.util.List;

/**
 * @author fmisser
 * @create 2021-05-10 下午2:00
 * @description
 */
public interface RedisService {
    boolean expire(String key, long ttl) throws RuntimeException;
    long getExpire(String key) throws RuntimeException;
    boolean hasKey(String key) throws RuntimeException;
    boolean delKey(String key) throws RuntimeException;
    boolean delKeys(List<String> keys) throws RuntimeException;
    Object get(String key) throws RuntimeException;
    boolean set(String key, Object value) throws RuntimeException;
    boolean set(String key, Object value, long ttl) throws RuntimeException;
    long listLeftPush(String key, Object... values) throws RuntimeException;
    Object listRightPop(String key) throws RuntimeException;
    List<Pair<String, String>> getList(List<String> keys) throws RuntimeException;
}

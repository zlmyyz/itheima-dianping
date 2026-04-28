package com.hmdp.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    private final  StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value,long Time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),Time,timeUnit);
    }

    public void setWithLogicalExpire(String key, Object value,long ExpireTime, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(ExpireTime)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R,ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> function,long Time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        // 1.从redis中查询缓存
        String Json = stringRedisTemplate.opsForValue().get(key);

        // 2.判断是否存在 (StrUtil.isNotBlank 只有有值才返回 true)
        if (StrUtil.isNotBlank(Json)) {
            // 3.存在，直接返回
            return JSONUtil.toBean(Json,type);
        }

        if(Json!=null){
            return null;
        }

        // 4.不存在，根据id查询数据库
        R r = function.apply(id);

        // 5.不存在，保存空值防止缓存穿透，返回错误
        if (r == null) {
            //保存空值到redis中
            stringRedisTemplate.opsForValue().set(key,"", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 6.存在，写入redis
        this.set(key,r,Time,timeUnit);
        // 7.返回
        return r;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <R,ID> R queryWithLogicalExpire(
            String keyPrefix,ID id, Class<R> type, Function<ID, R> function,long Time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        // 1.从redis中查询商铺缓存
        String Json = stringRedisTemplate.opsForValue().get(key);

        // 2.判断是否命中 (StrUtil.isNotBlank 只有有值才返回 true)
        if (StrUtil.isBlank(Json)) {
            // 3.未命中 ，直接返回
            return null;
        }

        // 4.命中，需要先把json对象反序列化为对象
        RedisData redisData = JSONUtil.toBean(Json, RedisData.class);
        R r= JSONUtil.toBean((JSONObject)redisData.getData(),type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //  5.判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            // 5.1.未过期，直接返回店铺信息
            return r;
        }

        // 5.2.过期，需要缓存重建
        // 6.缓存重建
        // 6.1获取互斥锁
        boolean isLock = tryLock(LOCK_SHOP_KEY + id);
        // 6.2.判断是否获取锁成功
        if(isLock){
            // 成功，先看缓存中是否有
            String json= stringRedisTemplate.opsForValue().get(key);
            RedisData doubleCheckData = JSONUtil.toBean(json, RedisData.class);
            if (doubleCheckData.getExpireTime().isAfter(LocalDateTime.now())) {
                // 别人已经更新好了，释放锁并返回最新数据
                unlock(LOCK_SHOP_KEY+ id);
                return JSONUtil.toBean((JSONObject) doubleCheckData.getData(), type);
            }
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                // 6.3.成功，开启独立线程，实现缓存重建
                try {
                    //查数据库
                    R r1 = function.apply(id);
                    //写入redis
                    this.setWithLogicalExpire(key,r1,Time,timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(LOCK_SHOP_KEY +id);
                }
            });
        }


        // 6.4.返回过期的店铺信息
        // 7.返回
        return r;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1");
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

}

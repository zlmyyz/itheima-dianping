package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements Ilock {

    private String name;
    private StringRedisTemplate redisTemplate;
    private  static final String KEY_PREFIX = "lock:";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT=new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    /*
    * 用UUID去做当前线程的标识，因为JVM用的自增逻辑，不同的JVM可能会出现线程ID一致的情况
    * */
    private static final String ID_PREFIX = UUID.randomUUID().toString(true)+"-";

    public SimpleRedisLock(StringRedisTemplate redisTemplate, String name) {
        this.redisTemplate = redisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(Long timeoutSec) {

        String thread = ID_PREFIX+Thread.currentThread().getId();

        Boolean success = redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, thread, timeoutSec, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        //调用lua脚本
        redisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX+name),
                ID_PREFIX+Thread.currentThread().getId()
        );
    }

//    @Override
//    public void unlock() {
//        //获取线程标识
//        String thread = ID_PREFIX+Thread.currentThread().getId();
//        //获取锁中的标识
//        String ID = redisTemplate.opsForValue().get(KEY_PREFIX + name);
//        /*
//        * 释放锁的时候要去判断标识是不是一样，防止释放别人的锁
//        * */
//        if(thread.equals(ID)) {
//            redisTemplate.delete(KEY_PREFIX + name);
//        }
//    }
}

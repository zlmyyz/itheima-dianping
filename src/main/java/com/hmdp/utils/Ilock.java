package com.hmdp.utils;

public interface Ilock {

    /*
    * 尝试获取锁
    * timeoutSec锁持有的过期时间，过期自动释放
    * return true获取成功，false失败
    * */

    boolean tryLock(Long timeoutSec);

    /*
    * 释放锁
    * */
    void unlock();

}

package com.drtd.utils;

/**
 * ClassName: ILock
 * Package: com.hmdp.utils
 * Description
 *  分布式锁
 * @Author zhl
 * @Create 2024-04-07 21:44
 * version 1.0
 */
public interface ILock {
    boolean tryLock(long timeoutSec);
    void unlock();
}

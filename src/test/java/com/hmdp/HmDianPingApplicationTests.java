package com.hmdp;

import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.context.ApplicationContext;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
public class HmDianPingApplicationTests {

    @Resource
    private IShopService shopService;

    @Resource
    private ApplicationContext applicationContext; // 现在它是 Spring 的了

    @Resource
    private RedisIdWorker redisIdWorker;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test// 确保导包是 org.junit.jupiter.api.Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };

        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }

//    @Test
//    public void testSaveShop(){
//        // 现在这里应该能正常运行了
//        if (applicationContext == null) {
//            System.err.println("❌ 容器没启动");
//            return;
//        }
//        if (shopService == null) {
//            System.err.println("❌ Service 没注入");
//            return;
//        }
//        shopService.saveShop2Redis(1L, 10L);
//        System.out.println("✅ 执行成功！");
//    }
}
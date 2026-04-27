package com.hmdp;

import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.context.ApplicationContext;

import javax.annotation.Resource;

@SpringBootTest
@RunWith(SpringRunner.class)
public class HmDianPingApplicationTests {

    @Resource
    private IShopService shopService;

    @Resource
    private ApplicationContext applicationContext; // 现在它是 Spring 的了

    @Test
    public void testSaveShop(){
        // 现在这里应该能正常运行了
        if (applicationContext == null) {
            System.err.println("❌ 容器没启动");
            return;
        }
        if (shopService == null) {
            System.err.println("❌ Service 没注入");
            return;
        }
        shopService.saveShop2Redis(1L, 10L);
        System.out.println("✅ 执行成功！");
    }
}
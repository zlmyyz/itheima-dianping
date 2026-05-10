package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.Ilock;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.locks.Lock;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;


    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.查询

        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        //2.判断秒杀是否开始

        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //秒杀尚未开始
            return Result.fail("秒杀尚未开始");
        }

        //3.判断秒杀是否结束

        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            //秒杀已经结束
            return Result.fail("秒杀已经结束");
        }

        //4.判断库存是否充足

        Integer stock = voucher.getStock();

        if (stock<1) {
            //库存不足
            return Result.fail("库存不足");
        }
//        Long userId = UserHolder.getUser().getId();
//        synchronized (userId.toString().intern()) {
//            //用特指锁缩小范围
//            //为什么锁放在进函数的时候？如果放在函数里，会导致事务没提交的时候锁就被释放了，也就可能导致并发问题
//            IVoucherOrderService proxy=(IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId,userId);
//            //必须拿事务的代理对象，不然事务可能会失效（事务要想生效是因为spring拿到了代理对象
//        }

        //处理并发问题基于redis的分布锁
        //创建锁对象
        Long userId = UserHolder.getUser().getId();
//        Ilock lock = new SimpleRedisLock(stringRedisTemplate,"order:"+userId);
        RLock lock = redissonClient.getLock("order:" + userId);
        //获取锁
        boolean isLock = lock.tryLock();
        //判断获取锁是否成功
        if (!isLock) {
            //获取失败
            return Result.fail("不允许重复下单");
        }
        try {
            IVoucherOrderService proxy=(IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId,userId);
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }

    }

    @Transactional
    public Result createVoucherOrder(Long voucherId, Long userId) {

            //5.一人一单
//            Long userId = UserHolder.getUser().getId();

            //5.1.查询订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();

            //5.2.判断是否存在
            if (count > 0) {
                return Result.fail("用户已经购买一次了");
            }

            //6.扣库存

//        voucher.setStock(voucher.getStock()-1);
//        boolean success = seckillVoucherService.update()
//                .setSql("stock=stock-1")
//                .eq("voucher_id", voucherId).eq("stock",stock)
//                .update();  //不用判断库存是否和之前相等，只用判断库存是否大于0，如果判断是否相等的话，效率太低
            boolean success = seckillVoucherService.update()
                    .setSql("stock=stock-1")
                    .eq("voucher_id", voucherId).gt("stock",0)
                    .update();

            if (!success) {
                return Result.fail("库存不足");
            }
            //7.创建订单

            VoucherOrder voucherOrder = new VoucherOrder();

            //7.1.订单ID

            Long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);

            //7.2.用户ID

            voucherOrder.setUserId(userId);

            //7.3.代金卷ID

            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);

            //8.返回订单id
            return Result.ok(orderId);

        }

}

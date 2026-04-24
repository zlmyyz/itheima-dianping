package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result list1() {
        // 1. 从 Redis 中查询缓存
        String key = LOCK_SHOP_KEY;
        String shopTypeJson = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断是否存在
        if (StrUtil.isNotBlank(shopTypeJson)) {
            // 3. 存在，将 JSON 字符串反序列化为 List 对象返回
//            log.debug("存在");
            List<ShopType> typeList = JSONUtil.toList(shopTypeJson, ShopType.class);
            return Result.ok(typeList);
        }

        // 4. 不存在，查询数据库
        // 注意：直接用 this 即可，不要注入自己
        List<ShopType> typeList = query().orderByAsc("sort").list();

        // 5. 数据库也没有，返回错误（或者存空值防止穿透）
        if (typeList == null || typeList.isEmpty()) {
            return Result.fail("分类不存在！");
        }

        // 6. 写入 Redis（将 List 转为 JSON 字符串）
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList),LOCK_SHOP_TTL, TimeUnit.MINUTES);

        // 7. 返回结果
        return Result.ok(typeList);

    }
}

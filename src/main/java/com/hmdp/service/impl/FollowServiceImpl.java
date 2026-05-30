package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import static com.hmdp.utils.RedisConstants.FOLLOW_USER_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

//    public FollowServiceImpl(StringRedisTemplate stringRedisTemplate) {
//        this.stringRedisTemplate = stringRedisTemplate;
//    }

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //1.获取当前登录的用户
        Long userId = UserHolder.getUser().getId();
        if (isFollow == null) {
            return Result.fail("参数错误");
        }
        //2.判断是关注还是取关
//        String key="follows:"+userId/;
        if(isFollow){
            //3.关注，新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                stringRedisTemplate.opsForSet().add(FOLLOW_USER_KEY,followUserId.toString());
                Long count = stringRedisTemplate.opsForSet().size(FOLLOW_USER_KEY);
                System.out.println(count+"=============================================");
            }
        }else{
//            baseMapper.deleteFollow(userId, followUserId);
            //4.取关，删除数据
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId).eq("follow_user_id", followUserId));
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(FOLLOW_USER_KEY,followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        //1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        //2.查询是否关注
        Integer count = query().eq("user_id",userId).eq("follow_user_id", followUserId).count();
        //3.判断
        return Result.ok(count>0);
    }
}

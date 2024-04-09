package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.hmdp.utils.RabbitMQConstants.*;

/**
 * 使用RabbitMQ 实现消息队列
 * @author zhl
 * @since 2021-12-22
 */
@Slf4j
@Service(value = "VoucherOrderServiceImplPlus")
public class VoucherOrderServiceImplPlus extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    /**
     * 全局id生成器
     */
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedissonClient redissonClient;
    // 使用 RabbitMQ 做消息队列
    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**
     * 生产者处理消息
     * @param voucherOrder
     */
    public void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        if (!isLock){
            log.error("请勿重复下单");
        }
        try {
            rabbitTemplate.convertAndSend(EXCHANGE_DIRECT,VOUCHERORDER_ROUTE_KEY,voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Result secKillVoucher(Long voucherId) {
        /**
         * 执行lua脚本，判断返回值是否=0
         * ==0，可以正常下单
         * ==1，库存不足
         * ==2，用户重复下单
         */
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString());
        int r = result.intValue();
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        long orderId = redisIdWorker.nextId("order");

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        // 把有购买资格的，把下单信息保存到 RabbitMQ
        handleVoucherOrder(voucherOrder);
        return Result.ok(orderId);
    }

    /**
     * 消费者处理优惠券订单
     * @param voucherOrder
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue("voucherOrder.queue1"),
            exchange = @Exchange(name = EXCHANGE_DIRECT, type = ExchangeTypes.DIRECT),
            key = {VOUCHERORDER_ROUTE_KEY}))
    @Override
    public void createVocherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        // 查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count > 0) {
            log.error("您已购买过");
        }
        // 库存--
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                // 实际上 stock > 0 是 CAS 以乐观锁的方式防止超卖  where voucher_id = ? and stock > 0 ?
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0)
                .update();
        if (!success) {
            log.error("库存不足");
        }
        // 订单++
        save(voucherOrder);
    }
}

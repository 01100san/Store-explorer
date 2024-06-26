package com.drtd.controller;


import com.drtd.dto.Result;
import com.drtd.service.IVoucherOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 实现优惠券的秒杀功能
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    // 注入以 RabbitMQ 的方式实现消息队列
    @Qualifier(value = "VoucherOrderServiceImplPlus")
    @Autowired
    private IVoucherOrderService voucherOrderService;

    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.secKillVoucher(voucherId);
    }
}

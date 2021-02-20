package com.fmisser.gtc.social.service.impl;

import com.fmisser.gtc.base.dto.im.ImAfterSendMsgDto;
import com.fmisser.gtc.base.dto.im.ImBeforeSendMsgDto;
import com.fmisser.gtc.base.dto.im.ImCbResp;
import com.fmisser.gtc.base.dto.im.ImStateChangeDto;
import com.fmisser.gtc.base.utils.DateUtils;
import com.fmisser.gtc.social.domain.*;
import com.fmisser.gtc.social.repository.*;
import com.fmisser.gtc.social.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class TencentImCallbackService implements ImCallbackService {

    private final AssetRepository assetRepository;

    private final UserRepository userRepository;

    private final MessageBillRepository messageBillRepository;

    private final CouponService couponService;

    private final CouponRepository couponRepository;

    private final ActiveRepository activeRepository;

    private final GreetService greetService;

    private final ImService imService;

    private final SysConfigService sysConfigService;

    public TencentImCallbackService(AssetRepository assetRepository,
                                    UserRepository userRepository,
                                    MessageBillRepository messageBillRepository,
                                    CouponService couponService,
                                    CouponRepository couponRepository,
                                    ActiveRepository activeRepository,
                                    GreetService greetService,
                                    ImService imService,
                                    SysConfigService sysConfigService) {
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
        this.messageBillRepository = messageBillRepository;
        this.couponService = couponService;
        this.couponRepository = couponRepository;
        this.activeRepository = activeRepository;
        this.greetService = greetService;
        this.imService = imService;
        this.sysConfigService = sysConfigService;
    }

    @Override
    public ImCbResp stateChangeCallback(ImStateChangeDto imStateChangeDto) {
        ImCbResp resp = new ImCbResp();
        resp.setActionStatus("OK");
        resp.setErrorCode(0);

        if (imStateChangeDto.getInfo().getAction().equals("Login")) {
            // 上线
            String digitId = imStateChangeDto.getInfo().getTo_Account();
            Optional<User> userOptional = userRepository.findByDigitId(digitId);
            if (userOptional.isPresent()) {
                // 更新用户状态
                // TODO: 2021/1/25 改成active service 中创建该逻辑
                Active active = new Active();
                active.setUserId(userOptional.get().getId());
                active.setIdentity(userOptional.get().getIdentity());
                active.setStatus(41);   // login
                activeRepository.save(active);

                // 打招呼功能
//                greetService.createGreet(userOptional.get());
            }
        }

        return resp;
    }

    @Override
    public ImCbResp beforeSendMsg(ImBeforeSendMsgDto imBeforeSendMsgDto) {
        ImCbResp resp = new ImCbResp();
        resp.setActionStatus("OK");
        resp.setErrorCode(0);

        if (sysConfigService.isAppAudit()) {
            // 审核中，消息聊天不扣费
            return resp;
        }

        // TODO: 2020/11/20 记录数据

        String userDigitIdFrom = imBeforeSendMsgDto.getFrom_Account();
        String userDigitIdTo = imBeforeSendMsgDto.getTo_Account();

        Optional<User> optionalUserFrom = userRepository.findByDigitId(userDigitIdFrom);
        Optional<User> optionalUserTo = userRepository.findByDigitId(userDigitIdTo);
        if (!optionalUserTo.isPresent() ||
                !optionalUserFrom.isPresent()) {

            // 用户数据错误
            resp.setActionStatus("FAIL");
            resp.setErrorCode(-1);
            resp.setErrorInfo("用户信息不正确");
            return resp;
        }

        User userFrom = optionalUserFrom.get();
        User userTo = optionalUserTo.get();
        if (userTo.getIdentity() == 0) {
            // 目标用户是普通用户，则不用考虑金币问题
            return resp;
        }

        BigDecimal messagePrice = userTo.getMessagePrice();
        if (Objects.isNull(messagePrice) || messagePrice.equals(BigDecimal.ZERO)) {
            // 没有设定价格 或者是0
            return resp;
        }

        Asset assetFrom = assetRepository.findByUserId(userFrom.getId());
        BigDecimal coinFrom = assetFrom.getCoin();
        // 优惠券信息转移到 Coupon
//        int msgFreeCoupon = assetFrom.getMsgFreeCoupon();

        int msgFreeCoupon;
        List<Coupon> couponList = couponService.getMsgFreeCoupon(userFrom);
        msgFreeCoupon = couponList.stream()
                .filter(couponService::isCouponValid)
                .map(Coupon::getCount)
                .reduce(0, Integer::sum);

        if (msgFreeCoupon <= 0 && coinFrom.compareTo(messagePrice) < 0) {
            // 没有免费券 同时 聊币不够
            resp.setErrorCode(121001);
            resp.setErrorInfo("聊币余额不足，请先充值");
        }

        return resp;
    }

    @Transactional
//    @Retryable
    @Override
    public ImCbResp afterSendMsg(ImAfterSendMsgDto imAfterSendMsgDto) {
        ImCbResp resp = new ImCbResp();
        resp.setActionStatus("OK");
        resp.setErrorCode(0);

        if (sysConfigService.isAppAudit()) {
            // 审核中消息聊天不扣费
            return resp;
        }

        // TODO: 2020/11/20 记录数据

        if (imAfterSendMsgDto.getSendMsgResult() != 0) {
            // 消息发送失败了，记录原因
            return resp;
        }

        String userDigitIdFrom = imAfterSendMsgDto.getFrom_Account();
        String userDigitIdTo = imAfterSendMsgDto.getTo_Account();

        Optional<User> optionalUserFrom = userRepository.findByDigitId(userDigitIdFrom);
        Optional<User> optionalUserTo = userRepository.findByDigitId(userDigitIdTo);
        if (!optionalUserTo.isPresent() ||
                !optionalUserFrom.isPresent()) {
            // 用户数据错误
            resp.setActionStatus("FAIL");
            resp.setErrorCode(-1);
            resp.setErrorInfo("用户信息不正确");
            return resp;
        }

        User userFrom = optionalUserFrom.get();
        User userTo = optionalUserTo.get();

        // 打招呼功能
        greetService.userReplyToday(userFrom, userTo, imAfterSendMsgDto.getMsgSeq());

        // 结算功能
        if (userTo.getIdentity() == 0) {
            // 目标身份是普通用户，则不用计算金币和收益
            return resp;
        }

        // 实际产生的原始金币费用等于主播设置的聊天价格
        BigDecimal messagePrice = userTo.getMessagePrice();
        if (Objects.isNull(messagePrice) || messagePrice.equals(BigDecimal.ZERO)) {
            // 没有设定价格 或者 价格是0
            return resp;
        }

        Asset assetFrom = assetRepository.findByUserId(userFrom.getId());
        Asset assetTo = assetRepository.findByUserId(userTo.getId());

        // 免费聊天券信息
//        int msgFreeCoupon = assetFrom.getMsgFreeCoupon();

        int msgFreeCoupon;
        List<Coupon> couponList = couponService.getMsgFreeCoupon(userFrom);
        msgFreeCoupon = couponList.stream()
                .filter(couponService::isCouponValid)
                .map(Coupon::getCount)
                .reduce(0, Integer::sum);

        // 再次判断金币是否足够，消息发送之前和发送之后可能金币发生了改动
        if (msgFreeCoupon <= 0 && assetFrom.getCoin().compareTo(messagePrice) < 0) {
            resp.setActionStatus("FAIL");
            resp.setErrorCode(121002);
            resp.setErrorInfo("用户金币不足");
            return resp;
        }

        // 生成流水对象
        MessageBill messageBill = new MessageBill();
        messageBill.setSerialNumber(createBillSerialNumber());
        messageBill.setUserIdFrom(userFrom.getId());
        messageBill.setUserIdTo(userTo.getId());
        messageBill.setMsgKey(imAfterSendMsgDto.getMsgKey());

        // 有券就直接扣券，使用券的话，主播没有收益
        if (msgFreeCoupon > 0) {
            // 扣券
//            assetRepository.subMsgFreeCoupon(assetFrom.getUserId(), 1);

            // 找到第一个可以扣免费券的
            Coupon availableCoupon = couponList.stream()
                    .filter(couponService::isCouponValid)
                    .findFirst()
                    .get();
            availableCoupon.setCount(availableCoupon.getCount() - 1);
            couponRepository.save(availableCoupon);

            // 扣券 主播没有收益

            // 流水数据
            messageBill.setSource(availableCoupon.getType());   // 免费券类型
            messageBill.setOriginCoin(BigDecimal.ZERO);
            messageBill.setCommissionRatio(BigDecimal.ZERO);
            messageBill.setCommissionCoin(BigDecimal.ZERO);
            messageBill.setProfitCoin(BigDecimal.ZERO);

            // 发送消息给发送者
//            imService.sendAfterSendMsg(userFrom, userTo,103, 0, 1);

        } else {
            // 扣金币
            assetRepository.subCoin(assetFrom.getUserId(), messagePrice);

            // 主播收益计算
            // 主播消息的收益比例
            BigDecimal profitRatio = assetTo.getMsgProfitRatio();
            BigDecimal commissionRatio = BigDecimal.ONE.subtract(profitRatio);
            BigDecimal commissionCoin = commissionRatio.multiply(messagePrice);
            // 直接加金币 去掉抽成价格
            BigDecimal coinProfit = messagePrice.subtract(commissionCoin);
            assetRepository.addCoin(assetTo.getUserId(), coinProfit);

            // 流水数据
            messageBill.setSource(0);
            messageBill.setOriginCoin(messagePrice);
            messageBill.setCommissionRatio(commissionRatio);
            messageBill.setCommissionCoin(commissionCoin);
            messageBill.setProfitCoin(coinProfit);

            // 发送消息给发送者
//            imService.sendAfterSendMsg(userFrom, userTo, 102, messagePrice.intValue(), 0);
        }

        // 保存流水
        messageBillRepository.save(messageBill);

        // TODO: 2020/11/20 记录用户扣金币以及主播加金币的详细日志

        return resp;
    }

    // 创建流水序列号
    public static String createBillSerialNumber() {
        // 订单号 = 当前时间戳 + int最大值
        return String.format("%d%010d",
                new Date().getTime(),
                new Random().nextInt(Integer.MAX_VALUE));
    }
}

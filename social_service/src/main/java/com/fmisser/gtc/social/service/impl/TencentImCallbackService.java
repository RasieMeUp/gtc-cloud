package com.fmisser.gtc.social.service.impl;

import com.fmisser.fpp.cache.redis.service.RedisService;
import com.fmisser.gtc.base.dto.im.*;
import com.fmisser.gtc.base.prop.ImConfProp;
import com.fmisser.gtc.social.domain.*;
import com.fmisser.gtc.social.repository.*;
import com.fmisser.gtc.social.service.*;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.tms.v20201229.TmsClient;
import com.tencentcloudapi.tms.v20201229.models.DetailResults;
import com.tencentcloudapi.tms.v20201229.models.TextModerationRequest;
import com.tencentcloudapi.tms.v20201229.models.TextModerationResponse;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Base64Utils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@AllArgsConstructor
public class TencentImCallbackService implements ImCallbackService {

    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final MessageBillRepository messageBillRepository;
    private final CouponService couponService;
    private final CouponRepository couponRepository;
    private final ActiveRepository activeRepository;
    private final GreetService greetService;
//    private final ImService imService;
    private final SysConfigService sysConfigService;
    private final ForbiddenService forbiddenService;
    private final ImConfProp imConfProp;
    private final ModerationService moderationService;
    private final UserMessageRepository userMessageRepository;
    private final RedisService redisService;

    @Override
    public Object stateChangeCallback(ImStateChangeDto imStateChangeDto) {
        ImCbResp resp = new ImCbResp();
        resp.setActionStatus("OK");
        resp.setErrorCode(0);

        if (imStateChangeDto.getInfo().getAction().equals("Login")) {
            // ??????
            String digitId = imStateChangeDto.getInfo().getTo_Account();
            Optional<User> userOptional = userRepository.findByDigitId(digitId);
            if (userOptional.isPresent()) {
                // ??????????????????
                // TODO: 2021/1/25 ??????active service ??????????????????
                Active active = new Active();
                active.setUserId(userOptional.get().getId());
                active.setIdentity(userOptional.get().getIdentity());
                active.setStatus(41);   // login
                activeRepository.save(active);

                // ???????????????
//                greetService.createGreet(userOptional.get());
            }
        }

        return resp;
    }

    @Override
    public Object beforeSendMsg(ImBeforeSendMsgDto imBeforeSendMsgDto, String originContent) {
        ImCbResp resp = new ImCbResp();
        resp.setActionStatus("OK");
        resp.setErrorCode(0);

        // ?????????redis
//        String redisKey = String.format("social:im:before:touser:%s", imBeforeSendMsgDto.getFrom_Account());
//        redisService.set(redisKey, originContent, 7 * 24 * 3600);

        // ????????????
        if (imBeforeSendMsgDto.getMsgBody().size() > 0) {
            for (ImMsgBody msgbody :
                    imBeforeSendMsgDto.getMsgBody()) {

                UserMessage userMessage = new UserMessage();
                userMessage.setDigitIdFrom(imBeforeSendMsgDto.getFrom_Account());
                userMessage.setDigitIdTo(imBeforeSendMsgDto.getTo_Account());
                userMessage.setMsgSeq(imBeforeSendMsgDto.getMsgSeq());
                userMessage.setMsgKey(imBeforeSendMsgDto.getMsgKey());
                userMessage.setMsgRandom(imBeforeSendMsgDto.getMsgRandom());
                userMessage.setMsgTime(imBeforeSendMsgDto.getMsgTime());

                if (msgbody.getMsgType().equals("TIMTextElem")) {
                    int ret = textModeration(imBeforeSendMsgDto.getFrom_Account(), msgbody.getMsgContent().getText(), "", 0);

                    log.info("[imcb] text blocked from: {}, to: {} with content: {}",
                            imBeforeSendMsgDto.getFrom_Account(),
                            imBeforeSendMsgDto.getTo_Account(),
                            msgbody.getMsgContent().getText());

                    userMessage.setMsgType(msgbody.getMsgType());
                    userMessage.setMsgText(msgbody.getMsgContent().getText());
                    userMessage.setMsgDesc(msgbody.getMsgContent().getDesc());
                    userMessage.setMsgData(msgbody.getMsgContent().getData());
                    userMessage.setPass(ret);
                    userMessageRepository.save(userMessage);

                    if (ret == 0) {
                        // ????????????????????????
                        ImTransferMsgCb transferMsgCb = new ImTransferMsgCb();
                        transferMsgCb.setActionStatus("OK");
                        transferMsgCb.setErrorCode(0);
                        ImMsgBody imMsgBody = new ImMsgBody();
                        imMsgBody.setMsgType("TIMTextElem");
                        ImMsgBody.ImMsgContent msgContent = new ImMsgBody.ImMsgContent();
                        msgContent.setText("[??????]");
                        imMsgBody.setMsgContent(msgContent);
                        transferMsgCb.setMsgBody(Collections.singletonList(imMsgBody));
                        return transferMsgCb;
                    }
                } else {
                    userMessage.setMsgType(msgbody.getMsgType());
                    userMessage.setMsgText(msgbody.getMsgContent().getText());
                    userMessage.setMsgDesc(msgbody.getMsgContent().getDesc());
                    userMessage.setMsgData(msgbody.getMsgContent().getData());
                    userMessage.setPass(1);
                    userMessageRepository.save(userMessage);
                }
            }
        }

        if (sysConfigService.isMsgFee()) {
            // ?????????????????????????????????
            return resp;
        }

        // ????????????????????????????????????
//        Optional<ImMsgBody> msgBody = imBeforeSendMsgDto.getMsgBody().stream()
//                .filter(imMsgBody -> imMsgBody.getMsgType().equals("TIMTextElem") &&
//                        textNoPrice(imMsgBody.getMsgContent().getText()))
//                .findAny();

        Optional<ImMsgBody> msgBody = imBeforeSendMsgDto.getMsgBody().stream()
                .filter(imMsgBody -> imMsgBody.getMsgType().equals("TIMCustomElem"))
                .findAny();

        if (msgBody.isPresent()) {
            return resp;
        }

        String userDigitIdFrom = imBeforeSendMsgDto.getFrom_Account();
        String userDigitIdTo = imBeforeSendMsgDto.getTo_Account();

        Optional<User> optionalUserFrom = userRepository.findByDigitId(userDigitIdFrom);
        Optional<User> optionalUserTo = userRepository.findByDigitId(userDigitIdTo);
        if (!optionalUserTo.isPresent() ||
                !optionalUserFrom.isPresent()) {

            // ??????????????????
            resp.setActionStatus("FAIL");
            resp.setErrorCode(121000);
            resp.setErrorInfo("?????????????????????");
            return resp;
        }

        User userFrom = optionalUserFrom.get();

        // ????????????
        Forbidden forbidden = forbiddenService.getUserForbidden(userFrom);
        if (forbidden != null) {
            resp.setActionStatus("FAIL");
            resp.setErrorCode(121002);
            if (forbidden.getDays() < 0) {
                resp.setErrorInfo("???????????????????????????????????????!");
            } else {
                resp.setErrorInfo("????????????????????????????????????????????????????????????!");
            }
            return resp;
        }

        User userTo = optionalUserTo.get();
//        if (userTo.getIdentity() == 0) {
//            // ?????????????????????????????????????????????????????????
//            return resp;
//        }

        if (userFrom.getIdentity() == 1 && userTo.getIdentity() == 1) {
            // ??????????????????????????????
            resp.setActionStatus("FAIL");
            resp.setErrorCode(-1);
            resp.setErrorInfo("????????????");
            return resp;
        }

        if (userFrom.getIdentity() == 0 && userTo.getIdentity() == 0) {
            // ??????????????????????????????
            resp.setActionStatus("FAIL");
            resp.setErrorCode(-1);
            resp.setErrorInfo("????????????");
            return resp;
        }

        BigDecimal messagePrice = userTo.getMessagePrice();
        if (Objects.isNull(messagePrice) || messagePrice.equals(BigDecimal.ZERO)) {
            // ?????????????????? ?????????0
            return resp;
        }

        Asset assetFrom = assetRepository.findByUserId(userFrom.getId());
        BigDecimal coinFrom = assetFrom.getCoin();
        // ???????????????????????? Coupon
//        int msgFreeCoupon = assetFrom.getMsgFreeCoupon();

        int msgFreeCoupon;
        List<Coupon> couponList = couponService.getMsgFreeCoupon(userFrom);
        msgFreeCoupon = couponList.stream()
                .filter(couponService::isCouponValid)
                .map(Coupon::getCount)
                .reduce(0, Integer::sum);

        if (msgFreeCoupon <= 0 && coinFrom.compareTo(messagePrice) < 0) {
            // ??????????????? ?????? ????????????
            resp.setErrorCode(121001);
            resp.setErrorInfo("?????????????????????????????????");
        }

        return resp;
    }

    @Transactional
//    @Retryable
    @Override
    public Object afterSendMsg(ImAfterSendMsgDto imAfterSendMsgDto, String originContent) {
        ImCbResp resp = new ImCbResp();
        resp.setActionStatus("OK");
        resp.setErrorCode(0);

        // ?????????redis
//        String redisKey = String.format("social:im:after:touser:%s", imAfterSendMsgDto.getFrom_Account());
//        redisService.set(redisKey, originContent, 7 * 24 * 3600);

        if (sysConfigService.isMsgFee()) {
            // ??????????????????????????????
            return resp;
        }

        // TODO: 2020/11/20 ????????????

        if (imAfterSendMsgDto.getSendMsgResult() != 0) {
            // ????????????????????????????????????
            return resp;
        }

        String userDigitIdFrom = imAfterSendMsgDto.getFrom_Account();
        String userDigitIdTo = imAfterSendMsgDto.getTo_Account();

        Optional<User> optionalUserFrom = userRepository.findByDigitId(userDigitIdFrom);
        Optional<User> optionalUserTo = userRepository.findByDigitId(userDigitIdTo);
        if (!optionalUserTo.isPresent() ||
                !optionalUserFrom.isPresent()) {
            // ??????????????????
            resp.setActionStatus("FAIL");
            resp.setErrorCode(121000);
            resp.setErrorInfo("?????????????????????");
            return resp;
        }

        User userFrom = optionalUserFrom.get();
        User userTo = optionalUserTo.get();

        // ???????????????
        greetService.userReplyToday(userFrom, userTo, imAfterSendMsgDto.getMsgSeq());


        // ????????????????????????????????????
//        Optional<ImMsgBody> msgBody = imAfterSendMsgDto.getMsgBody().stream()
//                .filter(imMsgBody -> imMsgBody.getMsgType().equals("TIMTextElem") &&
//                        textNoPrice(imMsgBody.getMsgContent().getText()))
//                .findAny();

        Optional<ImMsgBody> msgBody = imAfterSendMsgDto.getMsgBody().stream()
                .filter(imMsgBody -> imMsgBody.getMsgType().equals("TIMCustomElem"))
                .findAny();

        if (msgBody.isPresent()) {
            return resp;
        }

        // ????????????
        if (userTo.getIdentity() == 0) {
            // ????????????????????????????????????????????????????????????
            return resp;
        }

        // ??????????????????????????????????????????????????????????????????
        BigDecimal messagePrice = userTo.getMessagePrice();
        if (Objects.isNull(messagePrice) || messagePrice.equals(BigDecimal.ZERO)) {
            // ?????????????????? ?????? ?????????0
            return resp;
        }

        Asset assetFrom = assetRepository.findByUserId(userFrom.getId());
        Asset assetTo = assetRepository.findByUserId(userTo.getId());

        // ?????????????????????
//        int msgFreeCoupon = assetFrom.getMsgFreeCoupon();

        int msgFreeCoupon;
        List<Coupon> couponList = couponService.getMsgFreeCoupon(userFrom);
        msgFreeCoupon = couponList.stream()
                .filter(couponService::isCouponValid)
                .map(Coupon::getCount)
                .reduce(0, Integer::sum);

        // ?????????????????????????????????????????????????????????????????????????????????????????????
        if (msgFreeCoupon <= 0 && assetFrom.getCoin().compareTo(messagePrice) < 0) {
            resp.setActionStatus("FAIL");
            resp.setErrorCode(121002);
            resp.setErrorInfo("??????????????????");
            return resp;
        }

        // ??????????????????
        MessageBill messageBill = new MessageBill();
        messageBill.setSerialNumber(createBillSerialNumber());
        messageBill.setUserIdFrom(userFrom.getId());
        messageBill.setUserIdTo(userTo.getId());
        messageBill.setMsgKey(imAfterSendMsgDto.getMsgKey());

        // ????????????????????????????????????????????????????????????
        if (msgFreeCoupon > 0) {
            // ??????
//            assetRepository.subMsgFreeCoupon(assetFrom.getUserId(), 1);

            // ????????????????????????????????????
            Coupon availableCoupon = couponList.stream()
                    .filter(couponService::isCouponValid)
                    .findFirst()
                    .get();
            availableCoupon.setCount(availableCoupon.getCount() - 1);
            couponRepository.save(availableCoupon);

            // ?????? ??????????????????

            // ????????????
            messageBill.setSource(availableCoupon.getType());   // ???????????????
            messageBill.setOriginCoin(BigDecimal.ZERO);
            messageBill.setCommissionRatio(BigDecimal.ZERO);
            messageBill.setCommissionCoin(BigDecimal.ZERO);
            messageBill.setProfitCoin(BigDecimal.ZERO);

            // ????????????????????????
//            imService.sendAfterSendMsg(userFrom, userTo,103, 0, 1);

        } else {
            // ?????????
            assetRepository.subCoin(assetFrom.getUserId(), messagePrice);

            // ??????????????????
            // ???????????????????????????
            BigDecimal profitRatio = assetTo.getMsgProfitRatio();
            BigDecimal commissionRatio = BigDecimal.ONE.subtract(profitRatio);
            BigDecimal commissionCoin = commissionRatio.multiply(messagePrice);
            // ??????????????? ??????????????????
            BigDecimal coinProfit = messagePrice.subtract(commissionCoin);
            assetRepository.addCoin(assetTo.getUserId(), coinProfit);

            // ????????????
            messageBill.setSource(0);
            messageBill.setOriginCoin(messagePrice);
            messageBill.setCommissionRatio(commissionRatio);
            messageBill.setCommissionCoin(commissionCoin);
            messageBill.setProfitCoin(coinProfit);

            // ????????????????????????
//            imService.sendAfterSendMsg(userFrom, userTo, 102, messagePrice.intValue(), 0);
        }

        // ????????????
        messageBillRepository.save(messageBill);

        // TODO: 2020/11/20 ?????????????????????????????????????????????????????????

        return resp;
    }

    // ?????????????????????
    public static String createBillSerialNumber() {
        // ????????? = ??????????????? + int?????????
        return String.format("%d%010d",
                new Date().getTime(),
                new Random().nextInt(Integer.MAX_VALUE));
    }

    @SneakyThrows
    @Override
    public int textModeration(String userId, String text, String bizType, int moderationType) {

        text = Base64Utils.encodeToString(text.getBytes());

        Credential credential = new Credential(imConfProp.getSecretId(), imConfProp.getSecretKey());

        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint("tms.tencentcloudapi.com");

        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);

        TmsClient client = new TmsClient(credential, "ap-guangzhou", clientProfile);

        TextModerationRequest req = new TextModerationRequest();
        req.setContent(text);
        if (!StringUtils.isEmpty(bizType)) {
            req.setBizType(bizType);
        }
//        com.tencentcloudapi.tms.v20201229.models.User user = new com.tencentcloudapi.tms.v20201229.models.User();
//        user.setUserId(userId);
//        req.setUser(user);
//        req.setDataId("chat text");

        TextModerationResponse resp = client.TextModeration(req);

        List<Moderation> moderationList;
        if (moderationType == 0) {
            moderationList = moderationService.getModerationList();
        } else {
            moderationList = moderationService.getDynamicModerationList(moderationType);
        }

        DetailResults[] detailResults = resp.getDetailResults();
        for (DetailResults result :
                detailResults) {
            AtomicBoolean needBlock = new AtomicBoolean(false);
            moderationList.stream()
                    .filter(moderation -> {
                        return result.getLabel().equals(moderation.getLabel()) &&
                                result.getSuggestion().equals(moderation.getSuggestion()) &&
                                result.getScore() >= moderation.getScore();
                    })
                    .findAny()
                    .ifPresent(moderation -> needBlock.set(true));

            if (needBlock.get()) {
                return 0;
            }
        }

        return 1;
    }

    @Override
    public boolean textNoPrice(String text) {
        if (text.equals("Initiate calling") || text.equals("Cancel calling") ||
                text.equals("Accepted") || (text.contains("accepted") && text.length() < 20) ||
                (text.contains("is busy") && text.length() < 20) ||
                (text.contains("declined calling") && text.length() < 32) ||
                text.equals("Calling busy") ||
                text.equals("Calling declined") ||
                text.equals("Calling no reponse") ||
                text.equals("????????????") || text.equals("????????????") ||
                text.equals("?????????") || (text.contains("?????????") && text.length() < 20) ||
                (text.contains("??????") && text.length() < 20) ||
                (text.contains("????????????") && text.length() < 32) ||
                text.equals("????????????") ||
                text.equals("????????????") ||
                text.equals("?????????")
        ) {
            return true;
        } else {
            return false;
        }
    }
}

package com.fmisser.gtc.social.service.impl;

import com.fmisser.gtc.base.dto.social.RecvGiftDto;
import com.fmisser.gtc.base.exception.ApiException;
import com.fmisser.gtc.social.domain.Asset;
import com.fmisser.gtc.social.domain.Gift;
import com.fmisser.gtc.social.domain.GiftBill;
import com.fmisser.gtc.social.domain.User;
import com.fmisser.gtc.social.feign.PushFeign;
import com.fmisser.gtc.social.repository.AssetRepository;
import com.fmisser.gtc.social.repository.GiftBillRepository;
import com.fmisser.gtc.social.repository.GiftRepository;
import com.fmisser.gtc.social.service.CommonService;
import com.fmisser.gtc.social.service.GiftService;
import com.fmisser.gtc.social.service.GuardService;
import com.fmisser.gtc.social.service.ImService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.fmisser.gtc.social.service.impl.TencentImCallbackService.createBillSerialNumber;

@Slf4j
@Service
@AllArgsConstructor
public class GiftServiceImpl implements GiftService {

    private final GiftRepository giftRepository;
    private final GiftBillRepository giftBillRepository;
    private final AssetRepository assetRepository;
    private final ImService imService;
    private final GuardService guardService;
    private final PushFeign pushFeign;
    private final CommonService commonService;

    @Override
    public List<Gift> getGiftList() throws ApiException {
        return _prepareGiftResponse(giftRepository.getGiftList());
    }

    @Transactional
    @Override
    public int postGift(User fromUser, User toUser, Long giftId, Integer count) throws ApiException {

        Optional<Gift> giftOptional = giftRepository.findById(giftId);
        if (!giftOptional.isPresent()) {
            throw new ApiException(-1, "???????????????");
        }

        if (Objects.isNull(count) || count <= 0) {
            throw new ApiException(-1, "???????????????");
        }

        Gift gift = giftOptional.get();

        // TODO: 2021/1/4 ???????????????????????????

        BigDecimal price = gift.getPrice();
        BigDecimal totalPrice = price.multiply(BigDecimal.valueOf(count));

        Asset assetFrom = assetRepository.findByUserId(fromUser.getId());
        Asset assetTo = assetRepository.findByUserId(toUser.getId());

        if (assetFrom.getCoin().compareTo(totalPrice) < 0) {
            throw new ApiException(2001, "???????????????????????????!");
        }

        // ??????
        assetRepository.subCoin(fromUser.getId(), totalPrice);

        // ??????????????????
        BigDecimal profitRatio = assetTo.getGiftProfitRatio();
        BigDecimal commissionRatio = BigDecimal.ONE.subtract(profitRatio);
        BigDecimal commissionCoin = commissionRatio.multiply(totalPrice);
        // ??????????????? ??????????????????
        BigDecimal coinProfit = totalPrice.subtract(commissionCoin);
        assetRepository.addCoin(assetTo.getUserId(), coinProfit);

        // ??????????????????
        GiftBill giftBill = new GiftBill();
        giftBill.setSerialNumber(createBillSerialNumber());
        giftBill.setUserIdFrom(fromUser.getId());
        giftBill.setUserIdTo(toUser.getId());
        // ??????????????????uuid
        giftBill.setMsgKey(UUID.randomUUID().toString());
        giftBill.setGiftId(giftId);
        giftBill.setCommissionRatio(commissionRatio);
        giftBill.setCommissionCoin(commissionCoin);
        giftBill.setOriginCoin(totalPrice);
        giftBill.setProfitCoin(coinProfit);

        giftBillRepository.save(giftBill);


        final String[] headThumbnailUrl = new String[1];
        commonService.getUserProfileHeadCompleteUrl(fromUser.getHead())
                .ifPresent(v -> {
                    headThumbnailUrl[0] = v.getSecond();
                });

        // ????????????
        if (isGuardGift(gift)) {
            guardService.becomeGuard(fromUser, toUser);

            // ???????????????????????????
            String msgData = String.format("{\"tag\":%d,\"giftId\":%d,\"giftCount\":%d,\"giftName\":\"%s\",\"recvNick\":\"%s\",\"sendHead\":\"%s\",\"sendNick\":\"%s\"}",
                    1, giftId, count, gift.getName(), toUser.getNick(), headThumbnailUrl[0], fromUser.getNick());
            pushFeign.broadcast(msgData);
        }

        // ?????????????????????
        String msgData = String.format("{\"tag\":%d,\"giftId\":%d,\"giftCount\":%d,\"giftName\":\"%s\",\"recvNick\":\"%s\",\"sendHead\":\"%s\",\"sendNick\":\"%s\"}",
                2, giftId, count, gift.getName(), toUser.getNick(), headThumbnailUrl[0], fromUser.getNick());
        pushFeign.broadcast(msgData);

        // ??????????????????
        // TODO: 2021/1/4 ??????mq?????? notice service ??????
        imService.sendGiftMsg(fromUser, toUser, gift, count);

        return 1;
    }

    @Override
    public List<RecvGiftDto> getRecvGiftList(User user, int pageIndex, int pageSize) throws ApiException {
        List<RecvGiftDto> recvGiftDtoList = giftBillRepository.getRecvGiftList(user.getId());
        return _prepareRecvGiftResponse(recvGiftDtoList);
    }

    @Override
    public boolean isGuardGift(Gift gift) throws ApiException {
        return gift.getName().equals("??????");
    }

    private List<Gift> _prepareGiftResponse(List<Gift> giftList) {
        for (Gift gift: giftList) {
            if (!StringUtils.isEmpty(gift.getImage())) {
                String imageUrl = commonService.getSysImageCompleteUrl(gift.getImage());
                gift.setImageUrl(imageUrl);
            }
        }
        return giftList;
    }

    private List<RecvGiftDto> _prepareRecvGiftResponse(List<RecvGiftDto> recvGiftDtoList) {
        for (RecvGiftDto recvGiftDto: recvGiftDtoList) {
            if (!StringUtils.isEmpty(recvGiftDto.getGiftImage())) {
                String imageUrl = commonService.getSysImageCompleteUrl(recvGiftDto.getGiftImage());
                recvGiftDto.setGiftImageUrl(imageUrl);
            }
        }
        return recvGiftDtoList;
    }
}

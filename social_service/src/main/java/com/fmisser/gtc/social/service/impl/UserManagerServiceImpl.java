package com.fmisser.gtc.social.service.impl;

import com.fmisser.gtc.base.dto.social.*;
import com.fmisser.gtc.base.dto.social.calc.CalcConsumeDto;
import com.fmisser.gtc.base.dto.social.calc.CalcTotalProfitDto;
import com.fmisser.gtc.base.dto.social.calc.CalcUserDto;
import com.fmisser.gtc.base.exception.ApiException;
import com.fmisser.gtc.social.domain.*;
import com.fmisser.gtc.social.repository.*;
import com.fmisser.gtc.social.service.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;

@Slf4j
@Service
@AllArgsConstructor
public class UserManagerServiceImpl implements UserManagerService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final RecommendRepository recommendRepository;
    private final IdentityAuditRepository identityAuditRepository;
    private final AssetRepository assetRepository;
    private final LabelRepository labelRepository;
    private final IdentityAuditService identityAuditService;
    private final ImService imService;
    private final AsyncService asyncService;
    private final UserMaterialService userMaterialService;
    private final UserDeviceRepository userDeviceRepository;

    @Override
    public Pair<List<AnchorDto>,Map<String, Object>> getAnchorList(String digitId, String nick, String phone, Integer gender,
                                         Date startTime, Date endTime,
                                         int pageIndex, int pageSize,
                                         int sortColumn, int sortDirection,String channelId) throws ApiException {
//        Sort.Direction direction;
        String sortProp;
//        if (sortDirection == 0) {
//            direction = Sort.Direction.ASC;
//        } else {
//            direction = Sort.Direction.DESC;
//        }

        switch (sortColumn) {
            case 0:
                sortProp = "createTime";
                break;
            case 1:
                sortProp = "activeTime";
                break;
            case 2:
                sortProp = "coin";
                break;
            case 3:
                sortProp = "giftProfit";
                break;
            case 4:
                sortProp = "messageProfit";
                break;
            case 5:
                sortProp = "audioProfit";
                break;
            case 6:
                sortProp = "audioDuration";
                break;
            case 7:
                sortProp = "videoProfit";
                break;
            case 8:
                sortProp = "videoDuration";
                break;
            default:
                sortProp = "createTime";
                break;
        }

        if (sortDirection == 0) {
            sortProp += " ASC";
        } else {
            sortProp += " DESC";
        }

//        Pageable pageable = PageRequest.of(pageIndex - 1, pageSize, direction, sortProp);
//        Page<AnchorDto> anchorDtoPage = userRepository
//                .anchorStatistics(digitId, nick, phone, gender, startTime, endTime, pageable);

        List<AnchorDto> anchorDtoList = userRepository
                .anchorStatisticsEx2(digitId, nick, phone, gender, startTime, endTime, pageSize, (pageIndex - 1) * pageSize, channelId);

        Long totalCount = userRepository.countAnchorStatisticsEx(digitId, nick, phone, gender, startTime, endTime,channelId);
        Long totalPage = (totalCount / pageSize) + 1;

        Map<String, Object> extra = new HashMap<>();
        extra.put("totalPage", totalPage);
        extra.put("totalEle", totalCount);
        extra.put("currPage", pageIndex);
        extra.put("totalUser", totalCount);

        return Pair.of(anchorDtoList, extra);
    }

    @Override
    public Pair<List<ConsumerDto>,Map<String, Object>> getConsumerList(String digitId, String nick, String phone,
                                             Date startTime, Date endTime,
                                             int pageIndex, int pageSize,
                                             int sortColumn, int sortDirection,String  channelId) throws ApiException {
//        Sort.Direction direction;
        String sortProp;
//        if (sortDirection == 0) {
//            direction = Sort.Direction.ASC;
//        } else {
//            direction = Sort.Direction.DESC;
//        }

        switch (sortColumn) {
            case 0:
                sortProp = "createTime";
                break;
            case 1:
                sortProp = "activeTime";
                break;
            case 2:
                sortProp = "giftCoin";
                break;
            case 3:
                sortProp = "messageCoin";
                break;
            case 4:
                sortProp = "audioCoin";
                break;
            case 5:
                sortProp = "videoCoin";
                break;
            case 6:
                sortProp = "rechargeCoin";
                break;
            case 7:
                sortProp = "coin";
                break;
            default:
                sortProp = "createTime";
                break;
        }

        if (sortDirection == 0) {
            sortProp += " ASC";
        } else {
            sortProp += " DESC";
        }

//        Pageable pageable = PageRequest.of(pageIndex - 1, pageSize, direction, sortProp);
//        Page<ConsumerDto> consumerDtoPage = userRepository
//                .consumerStatistics(digitId, nick, phone, startTime, endTime, pageable);

        List<ConsumerDto> consumerDtoList = userRepository
                .consumerStatisticsEx2(digitId, nick, phone, startTime, endTime, pageSize, (pageIndex - 1) * pageSize, channelId);

        CalcConsumeDto calcConsumeDto = userRepository.calcConsume(digitId, nick, phone, startTime, endTime, channelId);
        Long totalCount = calcConsumeDto.getCount();
        Long totalPage = (totalCount / pageSize) + 1;

        Map<String, Object> extra = new HashMap<>();
        extra.put("totalPage", totalPage);
        extra.put("totalEle", totalCount);
        extra.put("currPage", pageIndex);
        extra.put("totalUser", calcConsumeDto.getCount());
        extra.put("totalRecharge", calcConsumeDto.getRecharge());

        return Pair.of(consumerDtoList, extra);
    }

    @Override
    public User getUserProfile(String digitId) throws ApiException {
        User user = userService.getUserByDigitId(digitId);
        // ????????????????????????
        Asset asset = assetRepository.findByUserId(user.getId());
        user.setCoin(asset.getCoin());
        // ??????????????????
        user.setVideoProfitRatio(asset.getVideoProfitRatio());
        user.setVoiceProfitRatio(asset.getVoiceProfitRatio());
        user.setMsgProfitRatio(asset.getMsgProfitRatio());
        user.setGiftProfitRatio(asset.getGiftProfitRatio());

        user.setBirthDay(user.getBirth());
        List<UserDevice> deviceList=new ArrayList<>();
        List<UserDevice> userDeviceList=  userDeviceRepository.findByUserId(user.getId());
        for(UserDevice userDevice:userDeviceList){
            if(userDevice.getDeviceAndroidId()!=null && userDevice.getDeviceAndroidId()!="" && !StringUtils.isEmpty( userDevice.getDeviceAndroidId())){
                deviceList.add(userDevice);
            }
        }

        List<UserDevice> userDeviceIdList=  deviceList.stream().collect(
                collectingAndThen(
                        toCollection(() -> new TreeSet<>(Comparator.comparing(UserDevice::getDeviceAndroidId))), ArrayList::new)
        );

        List<UserDevice> ipList=new ArrayList<>();
        List<UserDevice> userDeviceList2=  userDeviceRepository.findByUserId(user.getId());
        for (UserDevice userDevice:userDeviceList2){
            if(userDevice.getIpAddr()!=null && userDevice.getIpAddr()!="" && !StringUtils.isEmpty( userDevice.getIpAddr())){
                ipList.add(userDevice);
            }
        }
        List<UserDevice> userDeviceIPList=  ipList.stream().collect(
                collectingAndThen(
                        toCollection(() -> new TreeSet<>(Comparator.comparing(UserDevice::getIpAddr))), ArrayList::new)
        );


        user.setUserDeviceList(userDeviceIdList);
        user.setUserDeviceIpList(userDeviceIPList);


        return userService.profile(user);
    }

    @Override
    public User getUserProfileAudit(String digitId) throws ApiException {
        User user = userService.getUserByDigitId(digitId);

        // ????????????????????????
        // ????????????????????????????????????????????????????????????
        Optional<IdentityAudit> userProfileAudit = identityAuditService.getLastProfileAudit(user);
        Optional<IdentityAudit> userPhotosAudit = identityAuditService.getLastPhotosAudit(user);
        Optional<IdentityAudit> userVideoAudit = identityAuditService.getLastVideoAudit(user);
        Optional<IdentityAudit> userGuardPhotosAudit = identityAuditService.getLastGuardPhotosAudit(user);
        Optional<IdentityAudit> userAuditVideoAudit = identityAuditService.getLastAuditVideoAudit(user);
        userProfileAudit.ifPresent(identityAudit -> {
            if (identityAudit.getStatus() == 10) {
                // TODO: 2021/4/2 ??????mapper ??????
                if (Objects.nonNull(identityAudit.getHead())) {
                    user.setHead(identityAudit.getHead());
                }
                if (Objects.nonNull(identityAudit.getNick())) {
                    user.setNick(identityAudit.getNick());
                }
                if (Objects.nonNull(identityAudit.getBirth())) {
                    user.setBirth(identityAudit.getBirth());
                }
                if (Objects.nonNull(identityAudit.getCity())) {
                    user.setCity(identityAudit.getCity());
                }
                if (Objects.nonNull(identityAudit.getProfession())) {
                    user.setProfession(identityAudit.getProfession());
                }
                if (Objects.nonNull(identityAudit.getIntro())) {
                    user.setIntro(identityAudit.getIntro());
                }
                if (Objects.nonNull(identityAudit.getLabels())) {
                    String[] labelList = identityAudit.getLabels().split(",");
                    user.setLabels(_innerCreateLabels(labelList));
                }
                if (Objects.nonNull(identityAudit.getCallPrice())) {
                    user.setCallPrice(identityAudit.getCallPrice());
                }
                if (Objects.nonNull(identityAudit.getVideoPrice())) {
                    user.setVideoPrice(identityAudit.getVideoPrice());
                }
                if (Objects.nonNull(identityAudit.getMessagePrice())) {
                    user.setMessagePrice(identityAudit.getMessagePrice());
                }
                if (Objects.nonNull(identityAudit.getVoice())) {
                    user.setVoice(identityAudit.getVoice());
                }
            }
        });

        userPhotosAudit.ifPresent(identityAudit -> {
            if (Objects.nonNull(identityAudit.getPhotos())) {
                user.setPhotos(identityAudit.getPhotos());
            }
        });
//        // ??????????????????????????????
//        userPhotosAudit.ifPresent(identityAudit -> {
//            List<UserMaterial> photos = userMaterialService.getAuditPhotos(user);
//            if (photos.size() > 0) {
//                user.setOriginPhotos(photos);
//            }
//        });

        userVideoAudit.ifPresent(identityAudit -> {
            if (Objects.nonNull(identityAudit.getVideo())) {
                user.setVideo(identityAudit.getVideo());
            }
        });

        userGuardPhotosAudit.ifPresent(identityAudit -> {
            if (Objects.nonNull(identityAudit.getGuardPhotos())) {
                user.setGuardPhotos(identityAudit.getGuardPhotos());
            }
        });

        userAuditVideoAudit.ifPresent(identityAudit -> {
            if (Objects.nonNull(identityAudit.getAuditVideo())) {
                user.setAuditVideo(identityAudit.getAuditVideo());
            }
            if (Objects.nonNull(identityAudit.getAuditVideoCode())) {
                user.setVideoAuditCode(identityAudit.getAuditVideoCode());
            }
        });

        // ????????????????????????
        Asset asset = assetRepository.findByUserId(user.getId());
        user.setCoin(asset.getCoin());
        // ??????????????????
        user.setVideoProfitRatio(asset.getVideoProfitRatio());
        user.setVoiceProfitRatio(asset.getVoiceProfitRatio());
        user.setMsgProfitRatio(asset.getMsgProfitRatio());
        user.setGiftProfitRatio(asset.getGiftProfitRatio());

        user.setBirthDay(user.getBirth());



        return userService.profile(user);
    }

    @Override
    public Pair<List<RecommendDto>, Map<String, Object>> getRecommendList(String digitId, String nick, Integer gender,
                                                                          Integer type,String channelId, int pageIndex, int pageSize) throws ApiException {
        Pageable pageable = PageRequest.of(pageIndex - 1, pageSize);
        Page<RecommendDto> recommendDtoPage =
                recommendRepository.getRecommendList(digitId, nick, gender, type,channelId, pageable);

        Map<String, Object> extra = new HashMap<>();
        extra.put("totalPage", recommendDtoPage.getTotalPages());
        extra.put("totalEle", recommendDtoPage.getTotalElements());
        extra.put("currPage", pageIndex);

        return Pair.of(recommendDtoPage.getContent(), extra);
    }

    @Override
    public int configRecommend(String digitId, int type, int recommend, Long level,
                               Date startTime, Date endTime, Date startTime2, Date endTime2) throws ApiException {

        if (Objects.nonNull(level) && level < 1) {
            throw new ApiException(-1, "?????????????????????1");
        }

        if (Objects.nonNull(level) && level > 999999) {
            throw new ApiException(-1, "???????????????????????????999999");
        }

        User user = userService.getUserByDigitId(digitId);
        Optional<Recommend> optionalRecommend = recommendRepository.findByUserIdAndType(user.getId(), type);
        Recommend recommendDo;
        if (!optionalRecommend.isPresent()) {
            if (recommend == 0) {
                throw new ApiException(-1, "??????????????????????????????????????????");
            }
            recommendDo = new Recommend();
            recommendDo.setUserId(user.getId());
            recommendDo.setType(type);
            recommendDo.setRecommend(1);
            recommendDo.setLevel(level);

            // ???????????????????????????????????????
            if (type == 0 || type == 6 || type == 7 || type == 4) {
                // ???????????????????????????????????????
                recommendDo.setStartTime(startTime);
                recommendDo.setEndTime(endTime);
                recommendDo.setStartTime2(startTime2);
                recommendDo.setEndTime2(endTime2);
            }

        } else {
            recommendDo = optionalRecommend.get();
            recommendDo.setRecommend(recommend);
            if (Objects.nonNull(level)) {
                recommendDo.setLevel(level);
            }

            if (recommend == 1 && (type == 0 || type == 6 || type == 7 || type == 4)) {
                // ???????????????????????????????????????
                recommendDo.setStartTime(startTime);
                recommendDo.setEndTime(endTime);
                recommendDo.setStartTime2(startTime2);
                recommendDo.setEndTime2(endTime2);
            }
        }

        if (recommend == 1) {
            // ????????????
            List<Recommend> recommendList = recommendRepository
                    .findByTypeAndLevelGreaterThanEqualAndRecommend(type, level, 1);

            // ?????????????????????level?????????1
            List<Recommend> adjustList = recommendList
                    .stream()
                    .filter(r -> !r.getId().equals(recommendDo.getId()))
                    .peek(r -> r.setLevel(r.getLevel() + 1))
                    .collect(toList());

            adjustList.add(recommendDo);

            recommendRepository.saveAll(adjustList);
        } else {
            recommendRepository.save(recommendDo);
        }

        return 1;
    }

    @Override
    public Pair<List<IdentityAudit>, Map<String, Object>> getAnchorAuditList(String digitId, String nick, Integer gender, Integer status,
                                                  Date startTime, Date endTime,
                                                  int pageIndex, int pageSize) throws ApiException {

        // status 0???????????? 1??? ??????????????? 2??? ??????
        List<Integer> statusList = new ArrayList<>();
        if (status.equals(0)) {
            statusList.add(10);
        } else if (status.equals(1)) {
            statusList.add(20);
        } else {
            statusList.add(10);
            statusList.add(20);
        }

        Pageable pageable = PageRequest.of(pageIndex - 1, pageSize);
        Page<IdentityAudit> identityAuditPage = identityAuditRepository
                .getIdentityAuditList(digitId, nick, gender, statusList, startTime, endTime, pageable);

        Map<String, Object> extra = new HashMap<>();
        extra.put("totalPage", identityAuditPage.getTotalPages());
        extra.put("totalEle", identityAuditPage.getTotalElements());
        extra.put("currPage", pageIndex);

        return Pair.of(identityAuditPage.getContent(), extra);
    }

    @Override
    public List<IdentityAudit> getAnchorAudit(String digitId) throws ApiException {
        User user = userService.getUserByDigitId(digitId);
        return identityAuditRepository.getLatestWithAllType(user.getId());
    }

    @Transactional
//    @ReTry(value = {PessimisticLockingFailureException.class})
//    @Retryable(value = {PessimisticLockingFailureException.class})
    @Override
    public int anchorAudit(String serialNumber, int operate, String message) throws ApiException {
        IdentityAudit identityAudit = identityAuditRepository.findBySerialNumber(serialNumber);

        if (identityAudit.getStatus() != 10) {
            throw new ApiException(-1, "????????????????????????");
        }

        User user = userService.getUserById(identityAudit.getUserId());
        if (operate == 1) {
            // ????????????
            identityAudit.setStatus(30);

            // ????????????????????????
            int type = identityAudit.getType();
            Optional<IdentityAudit> identityAuditOptional = identityAuditService
                    .getLastAuditPrepare(identityAudit.getUserId(), type + 10);
            if (identityAuditOptional.isPresent()) {
                IdentityAudit identityAuditPrepare = identityAuditOptional.get();
                identityAuditPrepare.setStatus(30);
                identityAuditRepository.save(identityAuditPrepare);
            }

//            if (type == 2) {
//                // ??????????????? ??????????????????????????????????????????????????????????????????????????????????????????
//                List<UserMaterial> auditPreparePhotos = userMaterialService.getAuditPreparePhotos(user);
//                userMaterialService.deleteList(auditPreparePhotos);
//
//                List<UserMaterial> auditPhotos = userMaterialService.getAuditPhotos(user);
//                auditPhotos.forEach(userMaterial -> {
//                    userMaterial.setType(0);
//                });
//                userMaterialService.updateList(auditPhotos);
//            }
            if (type == 6) {
                user.setVideoAudit(1);
                userRepository.save(user);
            }

            switch (type) {
                case 1:
                    imService.sendToUser(null, user, "???????????????????????????????????????");
                    break;
                case 2:
                    imService.sendToUser(null, user, "?????????????????????????????????");
                    break;
                case 3:
                    imService.sendToUser(null, user, "?????????????????????????????????");
                    break;
                case 4:
                    imService.sendToUser(null, user, "???????????????????????????????????????");
                    break;
                case 6:
                    imService.sendToUser(null, user, "???????????????????????????????????????");
                    break;
            }

        } else {
            // ???????????????
            identityAudit.setStatus(20);

            String typeMsg;
            switch (identityAudit.getType()) {
                case 1:
                    typeMsg = "????????????";
                    break;
                case 2:
                    typeMsg = "??????";
                    break;
                case 3:
                    typeMsg = "??????";
                    break;
                case 4:
                    typeMsg = "????????????";
                    break;
                case 6:
                    typeMsg = "????????????";
                    break;
                default:
                    typeMsg = "??????";
            }
            String remarkMessage = StringUtils.isEmpty(message) ? "????????????" : message;
            String formatMsg = String.format("????????????%s?????????%s???????????????????????????", typeMsg, remarkMessage);
            imService.sendToUser(null, user, formatMsg);
        }

        if (Objects.nonNull(message)) {
            identityAudit.setMessage(message);
        }

        identityAuditRepository.save(identityAudit);

        if (operate == 1) {
            // ???????????????, ???????????????????????????
            if (Objects.nonNull(identityAudit.getHead())) {
                user.setHead(identityAudit.getHead());
            }

            if (Objects.nonNull(identityAudit.getNick())) {
                user.setNick(identityAudit.getNick());
            }

            if (Objects.nonNull(identityAudit.getBirth())) {
                user.setBirth(identityAudit.getBirth());
            }

            if (Objects.nonNull(identityAudit.getCity())) {
                user.setCity(identityAudit.getCity());
            }

            if (Objects.nonNull(identityAudit.getProfession())) {
                user.setProfession(identityAudit.getProfession());
            }

            if (Objects.nonNull(identityAudit.getIntro())) {
                user.setIntro(identityAudit.getIntro());
            }

            if (Objects.nonNull(identityAudit.getLabels())) {
                String[] labelList = identityAudit.getLabels().split(",");
                user.setLabels(_innerCreateLabels(labelList));
            }

            if (Objects.nonNull(identityAudit.getCallPrice())) {
                user.setCallPrice(identityAudit.getCallPrice());
            }

            if (Objects.nonNull(identityAudit.getVideoPrice())) {
                user.setVideoPrice(identityAudit.getVideoPrice());
            }

            if (Objects.nonNull(identityAudit.getMessagePrice())) {
                user.setMessagePrice(identityAudit.getMessagePrice());
            }

            if (Objects.nonNull(identityAudit.getPhotos())) {
                user.setPhotos(identityAudit.getPhotos());
            }

            if (Objects.nonNull(identityAudit.getVideo())) {
                user.setVideo(identityAudit.getVideo());
            }

            if (Objects.nonNull(identityAudit.getVoice())) {
                user.setVoice(identityAudit.getVoice());
            }

            if (Objects.nonNull(identityAudit.getGuardPhotos())) {
                user.setGuardPhotos(identityAudit.getGuardPhotos());
            }

            if (Objects.nonNull(identityAudit.getAuditVideo())) {
                user.setAuditVideo(identityAudit.getAuditVideo());
            }

            if (Objects.nonNull(identityAudit.getAuditVideoCode())) {
                user.setVideoAuditCode(identityAudit.getAuditVideoCode());
            }

            if (user.getIdentity() == 0) {
                // ???????????????????????????????????????,????????????????????????????????????
                boolean allPass = true;
                for (int type = 1; type <= 3; type++) {
                    if (type == identityAudit.getType()) {
                        continue;
                    }

                    Optional<IdentityAudit> identityAuditOther = identityAuditRepository
                            .findTopByUserIdAndTypeOrderByCreateTimeDesc(identityAudit.getUserId(), type);

                    if (!identityAuditOther.isPresent() || identityAuditOther.get().getStatus() != 30) {
                        allPass = false;
                    }
                }

                if (allPass) {
                    // ?????????????????????????????????????????????, ??????????????????????????????????????????
                    user.setIdentity(1);
                    userRepository.save(user);

                    // ????????????????????????
                    configRecommend(user.getDigitId(), 3, 1, 1L,
                            null, null, null, null);
                }
            }

            asyncService.setProfileAsync(user, 0L);
        }

        return 1;
    }

    @Override
    public Pair<List<CalcUserDto>, Map<String, Object>> getCalcUser(Date startTime, Date endTime, int pageIndex, int pageSize) throws ApiException {
        return null;
    }

    @Override
    public Pair<List<CalcTotalProfitDto>, Map<String, Object>> getCalcTotalProfit(Date startTime, Date endTime, int pageIndex, int pageSize) throws ApiException {
        return null;
    }

    @Deprecated
    @Override
    public int anchorVideoAudit(String digitId, int operate, String message) throws ApiException {
        log.info("[user manager] anchor video audit with digit id: {}, operate: {}, message: {}.",
                digitId, operate, message);

        if (operate == 1) {
            User user = userService.getUserByDigitId(digitId);
            user.setVideoAudit(1);
            userRepository.save(user);
        }

        return 1;
    }

    @Override
    public List<String> getBrandList() {
        return userRepository.getBrandList();
    }

    // ????????????
    private List<Label> _innerCreateLabels(String[] labels) {
        List<Label> labelList = new ArrayList<>();
        for (String name: labels) {
            Label label = labelRepository.findByName(name);
            if (label != null) {
                labelList.add(label);
            }
        }
        return labelList;
    }
}

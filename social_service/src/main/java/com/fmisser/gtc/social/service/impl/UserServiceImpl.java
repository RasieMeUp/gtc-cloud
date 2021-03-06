package com.fmisser.gtc.social.service.impl;

import com.fmisser.fpp.cache.redis.service.RedisService;
import com.fmisser.fpp.oss.abs.service.OssService;
import com.fmisser.fpp.oss.cos.service.CosService;
import com.fmisser.gtc.base.dto.im.ImQueryStateResp;
import com.fmisser.gtc.base.dto.social.AnchorCallStatusDto;
import com.fmisser.gtc.base.dto.social.ProfitConsumeDetail;
import com.fmisser.gtc.base.exception.ApiException;
import com.fmisser.gtc.base.i18n.SystemTips;
import com.fmisser.gtc.base.prop.OssConfProp;
import com.fmisser.gtc.base.utils.ArrayUtils;
import com.fmisser.gtc.base.utils.CryptoUtils;
import com.fmisser.gtc.base.utils.DateUtils;
import com.fmisser.gtc.social.domain.*;
import com.fmisser.gtc.social.mq.GreetDelayedBinding;
import com.fmisser.gtc.social.repository.*;
import com.fmisser.gtc.social.service.*;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.util.Pair;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@AllArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final LabelRepository labelRepository;
    private final OssConfProp ossConfProp;
    private final IdentityAuditService identityAuditService;
    private final AssetRepository assetRepository;
    private final BlockRepository blockRepository;
    private final FollowRepository followRepository;
    private final InviteRepository inviteRepository;
    private final CouponService couponService;
    private final SystemTips systemTips;
    private final GreetDelayedBinding greetDelayedBinding;
    private final SysConfigService sysConfigService;
    private final IdentityAuditRepository identityAuditRepository;
    private final ImService imService;
    private final CosService cosService;
    private final AsyncService asyncService;
    private final UserMaterialService userMaterialService;
    private final GuardService guardService;
    private final CommonService commonService;
    private final RedisService redisService;
    private final SysAppConfigService sysAppConfigService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    @Transactional
    @Override
    public User create(String phone, int gender, String nick, String inviteCode, String version,String channelId,String ipAdress,String deviceId,String email) throws ApiException {
        /****
        *????????????????????????IP????????????????????????????????????????????????????????????????????????????????????IP???redis??????????????????????????????2
         * ????????????2??????????????????????????????
         *  ????????????redis????????????????????????ip+1
         *  ??????????????????????????????????????????????????????
         *    ????????????????????????????????????
         *    ????????????????????????????????????????????????????????????redis??????????????????????????????2
         *      ????????????2???
         */
        if(!StringUtils.isEmpty(ipAdress) && ipAdress!="" && ipAdress!=null) {//??????????????????ip???????????????
            int k=0;
            int i=0;

            if(redisService.hasKey(ipAdress)){//??????IP??????????????????????????????????????????
                throw new ApiException(-1, "??????????????????????????????");
            }else{//?????????????????????redis????????????
                if(!redisService.hasKey(ipAdress+":create:user:ip:list")){//??????redis????????????,??????????????????
                    if(!StringUtils.isEmpty(deviceId) && deviceId!="" && deviceId!=null){
                        if(redisService.hasKey(deviceId)){//?????????????????????????????????
                            throw new ApiException(-1, "??????????????????????????????");
                        }else{//???????????????????????????redis????????????
                            if(!redisService.hasKey(ipAdress+":create:user:device:list")){
                                if(redisService.hasKey(deviceId+":"+"create:user:device:list")){
                                    Integer value=Integer.valueOf((String)redisService.get(deviceId+":"+"create:user:device:list"));
                                    k=value+1;
                                    redisService.delKey(deviceId+":"+"create:user:device:list");
                                }else{
                                    k++;
                                }
                                redisService.set(deviceId+":"+"create:user:device:list",k+"");

                                if(redisService.hasKey(ipAdress+":create:user:ip:list")){
                                    Integer value=Integer.valueOf((String)redisService.get(ipAdress+":"+"create:user:ip:list"));
                                    i=value+1;
                                    redisService.delKey(ipAdress+":create:user:ip:list");
                                }else{
                                    i++;
                                }
                                redisService.set(ipAdress+":create:user:ip:list",i+"");
                            }else{
                                if(Integer.valueOf((String)redisService.get(deviceId+":"+"create:user:ip:list"))<2){
                                    if(redisService.hasKey(deviceId+":"+"create:user:device:list")){
                                        Integer value=Integer.valueOf((String)redisService.get(deviceId+":"+"create:user:device:list"));
                                        k=value+1;
                                        redisService.delKey(deviceId+":"+"create:user:device:list");
                                    }else{
                                        k++;
                                    }
                                    redisService.set(deviceId+":"+"create:user:device:list",k+"");

                                    if(redisService.hasKey(ipAdress+":create:user:ip:list")){
                                        Integer value=Integer.valueOf((String)redisService.get(ipAdress+":"+"create:user:ip:list"));
                                        i=value+1;
                                        redisService.delKey(ipAdress+":create:user:ip:list");
                                    }else{
                                        i++;
                                    }
                                    redisService.set(ipAdress+":create:user:ip:list",i+"");
                                }else{
                                    throw new ApiException(-1, "??????????????????????????????");
                                }
                            }
                        }
                    }else{
                        i++;
                        redisService.set(ipAdress+":create:user:ip:list",i+"");
                    }
                }else{
                    Integer redisIpKey=Integer.valueOf((String)redisService.get(ipAdress+":"+"create:user:ip:list"));
                    if(redisIpKey<2){
                        if(!StringUtils.isEmpty(deviceId) && deviceId!="" && deviceId!=null){
                            //???????????????????????????????????????????????????????????????????????????
                            if(redisService.hasKey(deviceId)){
                                throw new ApiException(-1, "??????????????????????????????");
                            }else{//??????????????????????????????redis????????????
                                //1?????????redis????????????key ???????????????????????????value???1
                                if(!redisService.hasKey(deviceId+":"+"create:user:device:list")){
                                    if(redisService.hasKey(deviceId+":"+"create:user:device:list")){
                                        Integer value=Integer.valueOf((String)redisService.get(deviceId+":"+"create:user:device:list"));
                                        k=value+1;
                                        redisService.delKey(deviceId+":"+"create:user:device:list");
                                    }else{
                                        k++;
                                    }
                                    redisService.set(deviceId+":"+"create:user:device:list",k+"");
                                }else{//??????redis??????key??????????????????2?????????2?????????IP???redis???+1
                                    Integer deviceKey=Integer.valueOf((String)redisService.get(deviceId+":"+"create:user:device:list"));
                                    if(deviceKey<2){
                                        if(redisService.hasKey(deviceId+":"+"create:user:device:list")){
                                            Integer value=Integer.valueOf((String)redisService.get(deviceId+":"+"create:user:device:list"));
                                            k=value+1;
                                            redisService.delKey(deviceId+":"+"create:user:device:list");
                                        }else{
                                            k++;
                                        }
                                        redisService.set(deviceId+":"+"create:user:device:list",k+"");

                                        if(redisService.hasKey(ipAdress+":create:user:ip:list")){
                                            Integer value=Integer.valueOf((String)redisService.get(ipAdress+":"+"create:user:ip:list"));
                                            i=value+1;
                                            redisService.delKey(ipAdress+":create:user:ip:list");
                                        }else{
                                            i++;
                                        }
                                        redisService.set(ipAdress+":create:user:ip:list",i+"");
                                    }else{//??????????????????
                                        throw new ApiException(-1, "??????????????????????????????");
                                    }
                                }
                            }
                        }else{
                            if(redisService.hasKey(ipAdress+":create:user:ip:list")){
                                Integer value=Integer.valueOf((String)redisService.get(ipAdress+":"+"create:user:ip:list"));
                                i=value+1;
                                redisService.delKey(ipAdress+":create:user:ip:list");
                            }else{
                                i++;
                            }
                            redisService.set(ipAdress+":create:user:ip:list",i+"");
                        }
                    }/*else if(Integer.valueOf((String)redisService.get(ipAdress+":"+"create:user:ip:list"))<2){
                        if(redisService.hasKey(ipAdress+":create:user:ip:list")){
                            Integer value=Integer.valueOf((String)redisService.get(ipAdress+":"+"create:user:ip:list"));
                            i=value+1;
                            redisService.delKey(ipAdress+":create:user:ip:list");
                        }else{
                            i++;
                        }
                        redisService.set(ipAdress+":create:user:ip:list",i+"");
                    }*/ else{
                        throw new ApiException(-1, "??????????????????????????????");
                    }
                }
            }
        }
   /*********************************************************************************/
        // check exist
        if (userRepository.existsByUsername(phone)) {
            // ????????????
            throw new ApiException(-1, "???????????????");
        }

        // create
        User user = new User();
        user.setUsername(phone);
        user.setPhone(phone);
        user.setChannelId(channelId);

        Date date = new Date();
        Date startHour = DateUtils.getHourStart(date);
        Date endHour = DateUtils.getHourEnd(date);
        long count = userRepository.countByCreateTimeBetween(startHour, endHour);
        String digitId = calcDigitId(date, count + 1);
        user.setDigitId(digitId);

        if (StringUtils.isEmpty(nick)) {
            user.setNick(String.format("??????%s", digitId));
        } else {
            if (Objects.nonNull(getNick(nick))) {
                throw new ApiException(-1, "??????????????????");
            } else {
                user.setNick(nick);
            }
        }

        if(!StringUtils.isEmpty(email)){
         user.setEmail(email);
        }

        LocalDateTime defaultBirth = LocalDateTime.of(1990, 1, 1, 0, 0);
        user.setBirth(Date.from(defaultBirth.atZone(ZoneId.systemDefault()).toInstant()));
        user.setGender(gender);

        user.setLabels(new ArrayList<>());
//        user.setVerifyStatus(new VerifyStatus());
        user = userRepository.save(user);

        // ????????????
        Asset asset = new Asset();
        asset.setUserId(user.getId());
        asset.setVoiceProfitRatio(BigDecimal.valueOf(0.50));
        asset.setVideoProfitRatio(BigDecimal.valueOf(0.50));
        assetRepository.save(asset);

        // ??????????????????
        if (Objects.nonNull(inviteCode)) {
            // ????????????????????????????????????id
            Optional<User> inviteUser = userRepository.findByDigitId(inviteCode);
            if (!inviteUser.isPresent()) {
                throw new ApiException(-1, "????????????????????????!");
            }

            // ?????????????????????
            Invite invite = new Invite();
            invite.setUserId(inviteUser.get().getId());
            invite.setInvitedUserId(user.getId());
            invite.setInviteCode(inviteCode);
            invite.setType(0);
            inviteRepository.save(invite);
        }

        // ????????? ????????? ??? ?????????
        if (sysAppConfigService.getAppAuditVersion(version).equals(version) && sysAppConfigService.getAppAuditVersionTime(version)) {
            // ????????????????????? ?????????20????????????
            couponService.addCommMsgFreeCoupon(user.getId(), 20, 10);
        } else {
            // ???????????????
            if (sysConfigService.isRegSendFreeVideo()) {
                couponService.addCommVideoCoupon(user.getId(), 1, 10);
            }
            if (sysConfigService.isRegSendFreeMsg()) {
                couponService.addCommMsgFreeCoupon(user.getId(), 20, 10);
            }
        }


        // ???????????????????????????
        // ????????????????????????????????????????????????????????????????????????im?????????????????????????????????????????????????????????????????????im?????????
        // ????????????????????????, 5????????????
        String sendMsgPayload = String
                .format("3,%s,%s,%s", "", user.getId(), systemTips.assistNewUserMsg(user.getNick()));
        Message<String> welcomeDelayedMessage = MessageBuilder
                .withPayload(sendMsgPayload).setHeader("x-delay", 5 * 1000).build();
        boolean ret = greetDelayedBinding.greetDelayedOutputChannel().send(welcomeDelayedMessage);
        if (!ret) {
            // TODO: 2021/1/25 ??????????????????
        }

        // FIXME: 2021/5/22 ?????????????????????????????????????????????,
        //  ?????????????????????????????????????????????????????????5s??????,
        //  ???????????????????????????????????????im??????????????????????????????????????????????????????
        asyncService.setProfileAsync(user, 5000L);

        return _prepareResponse(user);
    }

    @Override
    public User getUserByUsername(String username) throws ApiException {
        Optional<User> userOptional = userRepository.findByUsername(username);
        if (!userOptional.isPresent()) {
            throw new ApiException(1005, "?????????????????????");
        }
        return userOptional.get();
    }

    @Override
    public boolean isUserExist(String username) throws ApiException {
        Optional<User> userOptional = userRepository.findByUsername(username);
        return userOptional.isPresent();
    }

    @Override
    public boolean isNickExist(String nick) throws ApiException {
        Optional<User> userOptional = userRepository.findByNick(nick);
        return userOptional.isPresent();
    }

    @Override
    public String getNick(String nick) throws ApiException {
        return userRepository.getNick(nick);
    }

    @Override
    public User getUserByDigitId(String digitId) throws ApiException {
        Optional<User> userOptional = userRepository.findByDigitId(digitId);
        if (!userOptional.isPresent()) {
            throw new ApiException(1005, "?????????????????????");
        }
        return userOptional.get();
    }

    public User getUserByDigitId2(String digitId) {
        Optional<User> userOptional = userRepository.findByDigitId(digitId);
        if (!userOptional.isPresent()) {
            return  null;
        }
        return userOptional.get();
    }

    @Override
    public User getAnchorByDigitIdPeace(String digitId) throws ApiException {
        Optional<User> userOptional = userRepository.findByDigitId(digitId);
        if (userOptional.isPresent()) {
            if (userOptional.get().getIdentity() == 1) {
                return userOptional.get();
            }
        }
        return null;
    }

    @Override
    public User getUserById(Long id) throws ApiException {
        Optional<User> userOptional = userRepository.findById(id);
        if (!userOptional.isPresent()) {
            throw new ApiException(1005, "?????????????????????");
        }
        return userOptional.get();
    }

    @Override
    public User profile(User user) throws ApiException {

        // ???????????? ??????????????????
//        user.setOriginPhotos(userMaterialService.getPhotos(user));

        return _prepareResponse(user);
    }

    @Override
    public User getSelfProfile(User user) throws ApiException {
        // ????????????????????????
        // ????????????????????????????????????????????????????????????
        Optional<IdentityAudit> userProfileAudit = identityAuditService.getLastProfileAudit(user);
        if (!userProfileAudit.isPresent() || userProfileAudit.get().getStatus() != 10) {
            userProfileAudit = identityAuditService.getLastProfilePrepare(user);
        }

        Optional<IdentityAudit> userPhotosAudit = identityAuditService.getLastPhotosAudit(user);
        if (!userPhotosAudit.isPresent() || userPhotosAudit.get().getStatus() != 10) {
            userPhotosAudit = identityAuditService.getLastPhotosPrepare(user);
        }

        Optional<IdentityAudit> userVideoAudit = identityAuditService.getLastVideoAudit(user);
        if (!userVideoAudit.isPresent() || userVideoAudit.get().getStatus() != 10) {
            userVideoAudit = identityAuditService.getLastVideoPrepare(user);
        }

        Optional<IdentityAudit> userGuardPhotosAudit = identityAuditService.getLastGuardPhotosAudit(user);
        if (!userGuardPhotosAudit.isPresent() || userGuardPhotosAudit.get().getStatus() != 10) {
            userGuardPhotosAudit = identityAuditService.getLastGuardPhotosPrepare(user);
        }

        Optional<IdentityAudit> userAuditVideoAudit = identityAuditService.getLastAuditVideoAudit(user);
        if (!userAuditVideoAudit.isPresent() || userAuditVideoAudit.get().getStatus() != 10) {
            userAuditVideoAudit = identityAuditService.getLastAuditVideoPrepare(user);
        }

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

//        // ????????????????????????
//        List<UserMaterial> photos;
//        Optional<IdentityAudit> userPhotosAudit = identityAuditService.getLastPhotosPrepare(user);
//        if (userPhotosAudit.isPresent()) {
//            // ??????????????????????????????????????????
//            photos = userMaterialService.getAuditPreparePhotos(user);
//        } else {
//            userPhotosAudit = identityAuditService.getLastPhotosAudit(user);
//            if (userPhotosAudit.isPresent() && userPhotosAudit.get().getStatus() == 10) {
//                // ??????????????????????????? ??????????????????
//                photos = userMaterialService.getAuditPhotos(user);
//            } else {
//                // ??????????????????
//                photos = userMaterialService.getPhotos(user);
//            }
//        }
//        user.setOriginPhotos(photos);
//
//        // ??????????????????????????????
//        UserMaterial auditVideoMaterial = userMaterialService.getAuditVideo(user);
//        if (Objects.nonNull(auditVideoMaterial)) {
//            user.setVideoAuditUrl(auditVideoMaterial.getName());
//        }

        return _prepareResponse(user);
    }

    @SneakyThrows
    @Override
    public User updateProfile(User user, Integer updateType,
                              String nick, String birth, String city, String profession,
                              String intro, String labels, String callPrice, String videoPrice, String messagePrice,
                              Integer mode, Integer rest, String restStartDate, String restEndDate,
                              Map<String, MultipartFile> multipartFileMap) throws ApiException {

        // ??????????????? 1: ??????????????????,??????????????????????????? 2: ????????????????????? 3: ????????????
        int optionType;
        IdentityAudit audit = null;

        if (updateType == 1) {
            // ?????????????????????

            // ??????????????????????????????
            Optional<IdentityAudit> identityAudit = identityAuditService.getLastProfileAudit(user);
            if (identityAudit.isPresent() &&
                    identityAudit.get().getStatus() == 10) {
                // ???????????????????????????
                throw new ApiException(-1, "?????????????????????????????????????????????");
            }

//            if (user.getIdentity() == 1) {
//                // ????????????????????????
//                optionType = 3;
//                audit = identityAuditService.createAuditPrepare(user, 1);
//            } else {
//                // ????????????????????????????????????
//                optionType = 2;
//                audit = identityAuditService.createAuditPrepare(user, 11);
//            }

            // ???????????????????????????????????????
            optionType = 3;
            audit = identityAuditService.createAuditPrepare(user, 11);

        } else {
            optionType = 1;
        }

        // ??????????????????
        if (nick != null && !nick.isEmpty()) {

            if (isNickExist(nick)) {
                throw new ApiException(-1, "????????????");
            }

            user.setNick(nick);
            if (optionType == 2 || optionType == 3) {
                audit.setNick(nick);
            }
        }
        if (birth != null && !birth.isEmpty()) {
            user.setBirth(new Date(Long.parseLong(birth)));
            if (optionType == 2 || optionType == 3) {
                audit.setBirth(new Date(Long.parseLong(birth)));
            }
        }
        if (city != null && !city.isEmpty()) {
            user.setCity(city);
            if (optionType == 2 || optionType == 3) {
                audit.setCity(city);
            }
        }
        if (profession != null && !profession.isEmpty()) {
            user.setProfession(profession);
            if (optionType == 2 || optionType == 3) {
                audit.setProfession(profession);
            }
        }
        if (intro != null && !intro.isEmpty()) {
            user.setIntro(intro);
            if (optionType == 2 || optionType == 3) {
                audit.setIntro(intro);
            }
        }
        if (labels != null && !labels.isEmpty()) {
            String[] labelList = labels.split(",");
            user.setLabels(_innerCreateLabels(labelList));
            if (optionType == 2 || optionType == 3) {
                audit.setLabels(labels);
            }
        }
        if (callPrice != null && !callPrice.isEmpty()) {
            BigDecimal price = BigDecimal.valueOf(Long.parseLong(callPrice));
            user.setCallPrice(price);
            if (optionType == 2 || optionType == 3) {
                audit.setCallPrice(price);
            }
        }
        if (videoPrice != null && !videoPrice.isEmpty()) {
            BigDecimal price = BigDecimal.valueOf(Long.parseLong(videoPrice));
            user.setVideoPrice(price);
            if (optionType == 2 || optionType == 3) {
                audit.setVideoPrice(price);
            }
        }
        if (messagePrice != null && !messagePrice.isEmpty()) {
            BigDecimal price = BigDecimal.valueOf(Long.parseLong(messagePrice));
            user.setMessagePrice(price);
            if (optionType == 2 || optionType == 3) {
                audit.setMessagePrice(price);
            }
        }
        if (Objects.nonNull(mode)) {
            user.setMode(mode);
        }
        if (Objects.nonNull(rest)) {
            user.setRest(rest);
        }
        if (Objects.nonNull(restStartDate)) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm");
            user.setRestStartDate(dateFormat.parse(restStartDate));
        }
        if (Objects.nonNull(restEndDate)) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm");
            user.setRestEndDate(dateFormat.parse(restEndDate));
        }

        // ????????????
        for (MultipartFile file: multipartFileMap.values()) {
            if (file.isEmpty()) {
                continue;
            }

            String randomUUID = UUID.randomUUID().toString();

            String name = file.getName();
            if (name.equals("voice")) {
                InputStream inputStream = file.getInputStream();
                String filename = file.getOriginalFilename();
                String suffixName = filename.substring(filename.lastIndexOf("."));
                if (!isAudioSupported(suffixName)) {
                    throw new ApiException(-1, "?????????????????????!");
                }

                // object name ????????? prefix/username_date_randomUUID.xxx
                String objectName = String.format("%s%s_%s_%s%s",
                        ossConfProp.getCosUserProfileVoicePrefix(),
                        CryptoUtils.base64AesSecret(user.getUsername(), ossConfProp.getObjectAesKey()),
                        new Date().getTime(),
                        randomUUID,
                        suffixName);
                cosService.putObject(ossConfProp.getUserDynamicCosBucket(),
                        ossConfProp.getCosUserProfileRootPath() + objectName,
                        inputStream, file.getSize(), "audio/mpeg");

                user.setVoice(objectName);
                if (optionType == 2 || optionType == 3) {
                    audit.setVoice(objectName);
                }

            } else if (name.equals("head")) {
                InputStream inputStream = file.getInputStream();
                String filename = file.getOriginalFilename();
                String suffixName = filename.substring(filename.lastIndexOf("."));
                if (!isPictureSupported(suffixName)) {
                    throw new ApiException(-1, "?????????????????????!");
                }

                // ??????????????????
                String objectName = String.format("%s%s_%s_%s%s",
                        ossConfProp.getCosUserProfileHeadPrefix(),
                        CryptoUtils.base64AesSecret(user.getUsername(), ossConfProp.getObjectAesKey()),
                        new Date().getTime(),
                        randomUUID,
                        suffixName);

                cosService.putObject(ossConfProp.getUserDynamicCosBucket(),
                        ossConfProp.getCosUserProfileRootPath() + objectName,
                        inputStream, file.getSize(), "image/png");

                user.setHead(objectName);
                if (optionType == 2 || optionType == 3) {
                    audit.setHead(objectName);
                }
            }
        }

        if (optionType == 1) {
            user = userRepository.save(user);

            asyncService.setProfileAsync(user, 0L);

            return _prepareResponse(user);
        } else {
            identityAuditRepository.save(audit);

            // ?????????????????????????????????????????????
//            return _prepareResponse(user);
            return getSelfProfile(user);
        }
    }

    @Override
    @SneakyThrows
    public User updatePhotos(User user, Integer updateType,
                             String existsPhotos, Map<String, MultipartFile> multipartFileMap) throws ApiException {

        // ??????????????? 1: ?????????????????????2: ????????????????????? 3: ????????????
        int optionType;
        IdentityAudit audit = null;

        if (updateType == 1) {
            // ?????????????????????

            // ??????????????????????????????
            Optional<IdentityAudit> identityAudit = identityAuditService.getLastPhotosAudit(user);

            if (identityAudit.isPresent() &&
                    identityAudit.get().getStatus() == 10) {
                // ???????????????????????????
                throw new ApiException(-1, "?????????????????????????????????????????????");
            }

//            if (user.getIdentity() == 1) {
//                // ????????????????????????
//                optionType = 3;
//                audit = identityAuditService.createAuditPrepare(user, 2);
//            } else {
//                // ????????????????????????????????????
//                optionType = 2;
//                audit = identityAuditService.createAuditPrepare(user, 12);
//            }

            // ???????????????????????????????????????
            optionType = 3;
            audit = identityAuditService.createAuditPrepare(user, 12);
        } else {
            optionType = 1;
        }

        List<String> photoList = new ArrayList<>();

        for (MultipartFile file: multipartFileMap.values()) {
            if (file.isEmpty()) {
                continue;
            }

            String randomUUID = UUID.randomUUID().toString();

            InputStream inputStream = file.getInputStream();
            String filename = file.getOriginalFilename();
            String suffixName = filename.substring(filename.lastIndexOf("."));

            if (!isPictureSupported(suffixName)) {
                throw new ApiException(-1, "?????????????????????!");
            }

            String objectName = String.format("%s%s_%s_%s%s",
                    ossConfProp.getCosUserProfilePhotoPrefix(),
                    CryptoUtils.base64AesSecret(user.getUsername(), ossConfProp.getObjectAesKey()),
                    new Date().getTime(),
                    randomUUID,
                    suffixName);

            cosService.putObject(ossConfProp.getUserDynamicCosBucket(),
                    ossConfProp.getCosUserProfileRootPath() + objectName,
                    inputStream, file.getSize(), "image/png");

            if (!StringUtils.isEmpty(objectName)) {
                photoList.add(objectName);
            }
        }

//        if (photoList.size() == 0 ) {
//            throw new ApiException(-1, "??????????????????,???????????????");
//        }

        String originPhotosString;
        if (Objects.nonNull(audit.getPhotos())) {
            originPhotosString = audit.getPhotos();
        } else {
            originPhotosString = user.getPhotos();
        }
        if (Objects.nonNull(originPhotosString) && !StringUtils.isEmpty(existsPhotos)) {
            // ???????????????????????????
            List<String> originPhotos = changePhotosToList(originPhotosString);
            List<String> existPhotos = changePhotosToList(existsPhotos);

            List<String> filterPhotos = originPhotos.stream()
                    .filter(existPhotos::contains)
                    .collect(Collectors.toList());

            if (photoList.size() > 0) {
                filterPhotos.addAll(photoList);
            }

            user.setPhotos(filterPhotos.toString());
            if (optionType == 2 || optionType == 3) {
                audit.setPhotos(filterPhotos.toString());
            }
        } else {
            if (photoList.size() > 0) {

                user.setPhotos(photoList.toString());
                if (optionType == 2 || optionType == 3) {
                    audit.setPhotos(photoList.toString());
                }
            }
        }

        if (optionType == 1) {
            user = userRepository.save(user);
            return _prepareResponse(user);
        } else {
            identityAuditRepository.save(audit);
//            return _prepareResponse(user);
            return getSelfProfile(user);
        }
    }

    @Override
    @SneakyThrows
    @Deprecated
    public User updateVerifyImage(User user,
                                  Map<String, MultipartFile> multipartFileMap) throws ApiException {

        if (user.getIdentity() == 1) {
            // TODO: 2020/11/30 ???????????????????????????????????????????????????????????????
        }

        for (MultipartFile file: multipartFileMap.values()) {
            if (file.isEmpty()) {
                continue;
            }

            String randomUUID = UUID.randomUUID().toString();

            InputStream inputStream = file.getInputStream();
            String filename = file.getOriginalFilename();
            String suffixName = filename.substring(filename.lastIndexOf("."));

            if (!isPictureSupported(suffixName)) {
                throw new ApiException(-1, "?????????????????????!");
            }

            String objectName = String.format("%s%s_%s_%s%s",
                    ossConfProp.getCosUserProfileVerifyImagePrefix(),
                    CryptoUtils.base64AesSecret(user.getUsername(), ossConfProp.getObjectAesKey()),
                    new Date().getTime(),
                    randomUUID,
                    suffixName);

            cosService.putObject(ossConfProp.getUserDynamicCosBucket(),
                    ossConfProp.getCosUserProfileRootPath() + objectName,
                    inputStream, file.getSize(), "image/png");

            user.setSelfie(objectName);

            // ????????????????????????????????????
            break;
        }

        if (user.getSelfie() == null || user.getSelfie().isEmpty()) {
            throw new ApiException(-1, "?????????????????????");
        }

        user = userRepository.save(user);

        return _prepareResponse(user);
    }

    @SneakyThrows
    @Override
    public User updateVideo(User user, Integer updateType,
                            Map<String, MultipartFile> multipartFileMap) throws ApiException {

        // ??????????????? 1: ?????????????????????2: ????????????????????? 3: ????????????
        int optionType;
        IdentityAudit audit = null;

        if (updateType == 1) {
            // ?????????????????????

            // ??????????????????????????????
            Optional<IdentityAudit> identityAudit = identityAuditService.getLastVideoAudit(user);

            if (identityAudit.isPresent() &&
                    identityAudit.get().getStatus() == 10) {
                // ???????????????????????????
                throw new ApiException(-1, "?????????????????????????????????????????????");
            }

//            if (user.getIdentity() == 1) {
//                // ????????????????????????
//                optionType = 3;
//                audit = identityAuditService.createAuditPrepare(user, 3);
//            } else {
//                // ????????????????????????????????????
//                optionType = 2;
//                audit = identityAuditService.createAuditPrepare(user, 13);
//            }

            // ???????????????????????????????????????
            optionType = 3;
            audit = identityAuditService.createAuditPrepare(user, 13);
        } else {
            optionType = 1;
        }

        for (MultipartFile file: multipartFileMap.values()) {
            if (file.isEmpty()) {
                continue;
            }

            String randomUUID = UUID.randomUUID().toString();

            InputStream inputStream = file.getInputStream();
            String filename = file.getOriginalFilename();
            String suffixName = filename.substring(filename.lastIndexOf("."));

            if (!isVideoSupported(suffixName)) {
                throw new ApiException(-1, "?????????????????????!");
            }

            // ????????????????????????
            String objectName = String.format("%s%s_%s_%s%s",
                    ossConfProp.getCosUserProfileVideoPrefix(),
                    CryptoUtils.base64AesSecret(user.getUsername(), ossConfProp.getObjectAesKey()),
                    new Date().getTime(),
                    randomUUID,
                    suffixName);

            cosService.putObject(ossConfProp.getUserDynamicCosBucket(),
                    ossConfProp.getCosUserProfileRootPath() + objectName,
                    inputStream, file.getSize(), "video/mp4");

            user.setVideo(objectName);
            if (optionType == 2 || optionType == 3) {
                audit.setVideo(objectName);
            }

            // ????????????????????????????????????
            break;
        }

        if (user.getVideo() == null || user.getVideo().isEmpty()) {
            throw new ApiException(-1, "?????????????????????");
        }

        if (optionType == 1) {
            user = userRepository.save(user);
            return _prepareResponse(user);
        } else {
            identityAuditRepository.save(audit);
//            return _prepareResponse(user);
            return getSelfProfile(user);
        }
    }

    @Override
    public int logout(User user) throws ApiException {
        // TODO: 2020/11/21 ????????????im????????????
        // TODO: 2020/11/26 ????????????
        return 1;
    }

//    @Cacheable(cacheNames = "anchorList", key = "#type+':'+#gender+':'+#pageIndex+':'+#pageSize")
    @SneakyThrows
    @Override
    public List<User> getAnchorList(Integer type, Integer gender, int pageIndex, int pageSize) throws ApiException {

        // ???????????????type ???????????????
        // type = 0 ???????????????????????? ?????????????????????????????????????????????

        // type = 1 ?????????????????????

        // type = 2 ????????????????????????

//        List<User> userList =
//                userRepository.getAnchorListByCreateTime(gender, pageable).getContent();
        if (type == 0) {
//            userPage = userRepository.getAnchorListBySystemAndFollow(gender, pageable);
            SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
            Date finalNow = dateFormat.parse(dateFormat.format(new Date()));

            if (sysConfigService.isRandRecommend()) {
                // ??? 0 6 7 ??????????????????
                List<Integer> randTypeList = Arrays.asList(0, 6, 7);
                Random random = new Random();
                int n = random.nextInt(randTypeList.size());

                List<User> userList = userRepository.
                        getAnchorListBySystemAndActive(finalNow, gender, pageSize, pageSize * pageIndex, randTypeList.get(n));
                return userList.stream()
                        .map(this::_prepareResponse)
                        .collect(Collectors.toList());
            } else {
                List<User> userList = userRepository.
                        getAnchorListBySystemAndActive(finalNow, gender, pageSize, pageSize * pageIndex, 0);
                return userList.stream()
                        .map(this::_prepareResponse)
                        .collect(Collectors.toList());
            }

        } else if (type == 1) {
            // TODO: 2021/3/6 ???????????????????????? ??????????????????pageable ????????????sql??????
//            Pageable pageable = PageRequest.of(pageIndex, pageSize);
//            Page<User> userPage;
//            userPage = userRepository.getAnchorListByProfitEx(gender, pageable);
//            return userPage.stream()
//                    .map(this::_prepareResponse)
//                    .collect(Collectors.toList());

            // ???????????????????????????
            Date now = new Date();
            Date tenDaysAgo = new Date(now.getTime() - 10 * 24 * 3600 * 1000);
            List<User> userList = userRepository.getAnchorListByActive(gender, tenDaysAgo);

            if (userList.isEmpty()) {
                // ???????????????
                return userList;
            }

            List<String> userDigitList =
                    userList.stream()
                    .map(User::getDigitId)
                    .collect(Collectors.toList());

            // ???????????????????????????
            ImQueryStateResp imQueryStateResp = imService.queryState(userDigitList);
            List<ImQueryStateResp.QueryResult> queryResultList = imQueryStateResp.getQueryResult();
            List<String> onlineUserList = queryResultList
                    .stream()
                    .filter(queryResult -> queryResult.getStatus().equals("Online"))
                    .map(ImQueryStateResp.QueryResult::getTo_Account)
                    .collect(Collectors.toList());

            // ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
            List<String> sortedOnlineUserList = userDigitList
                    .stream()
                    .filter(onlineUserList::contains)
                    .collect(Collectors.toList());

            List<String> pushOnlineUserList = queryResultList
                    .stream()
                    .filter(queryResult -> queryResult.getStatus().equals("PushOnline"))
                    .map(ImQueryStateResp.QueryResult::getTo_Account)
                    .collect(Collectors.toList());

            List<String> sortedPushOnlineUserList = userDigitList
                    .stream()
                    .filter(pushOnlineUserList::contains)
                    .collect(Collectors.toList());

            List<String> offlineUserList = queryResultList
                    .stream()
                    .filter(queryResult -> queryResult.getStatus().equals("Offline"))
                    .map(ImQueryStateResp.QueryResult::getTo_Account)
                    .collect(Collectors.toList());

            List<String> sortedOfflineUserList = userDigitList
                    .stream()
                    .filter(offlineUserList::contains)
                    .collect(Collectors.toList());

//            if (imQueryStateResp.getErrorList() != null) {
//                List<String> errorUserList = imQueryStateResp.getErrorList()
//                        .stream()
//                        .map(ImQueryStateResp.QueryResult::getTo_Account)
//                        .collect(Collectors.toList());
//            }

            // ?????? ????????????????????????????????????????????????????????????
            List<String> sortedUserList = Stream.of(sortedOnlineUserList, sortedPushOnlineUserList, sortedOfflineUserList)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());

            // ??????
            int totalCount = sortedUserList.size();
            int offset = pageIndex * pageSize;
            if (offset >= totalCount) {
                // ?????????
                return new ArrayList<>();
            }
            List<String> pageUserList = sortedUserList.subList(offset, Math.min(offset + pageSize, totalCount));
            return pageUserList.stream()
                    .map(s -> userList.get(userDigitList.indexOf(s)))
                    .map(this::_prepareResponse)
                    .collect(Collectors.toList());
        } else {
            Pageable pageable = PageRequest.of(pageIndex, pageSize);
            Page<User> userPage;
            userPage = userRepository.getAnchorListByCreateTime(gender, pageable);
            return userPage.stream()
                    .map(this::_prepareResponse)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public List<User> getAuditAnchorList(Integer type, Integer gender, int pageIndex, int pageSize) throws ApiException {
        Pageable pageable = PageRequest.of(pageIndex, pageSize);
        Page<User> userPage = userRepository.getAuditAnchorList(gender, pageable);

        return userPage.stream()
                .map(this::_prepareResponse)
                .collect(Collectors.toList());
    }

    @Override
    public User getAnchorProfile(User user, User selfUser) throws ApiException {
        Long selfUserId = 0L;
        if (Objects.nonNull(selfUser)) {
            selfUserId = selfUser.getId();
        }

        User finalUser = profile(user);

        // ????????????????????????
        List<Integer> types = Arrays.asList(10, 20);
        List<Block> blockList = blockRepository
                .findByUserIdAndBlockUserIdAndTypeIsIn(selfUserId, finalUser.getId(), types);

        blockList.forEach(block -> {
            if (block.getBlock() == 1 && block.getType() == 10) {
                finalUser.setBlockDynamic(1);
            } else {
                finalUser.setBlockDynamic(0);
            }

            if (block.getBlock() == 1 && block.getType() == 20) {
                finalUser.setBlockChat(1);
            } else {
                finalUser.setBlockChat(0);
            }
        });

        // ????????????????????????
        Follow follow = followRepository.findByUserIdFromAndUserIdTo(selfUserId, finalUser.getId());
        if (Objects.nonNull(follow) && follow.getStatus() == 1) {
            finalUser.setIsFollow(1);
        } else {
            finalUser.setIsFollow(0);
        }

        // ????????????????????????
        finalUser.setIsGuard(0);
        if (Objects.nonNull(selfUser)) {
            boolean isGuard = guardService.isGuard(selfUser, user);
            if (isGuard) {
                finalUser.setIsGuard(1);
            }
        }

        return finalUser;
    }

    @Override
    public List<User> getRandAnchorList(int count) throws ApiException {
        return userRepository.findRandAnchorList(count);
    }

    @SneakyThrows
    @Override
    public Integer callPreCheck(User fromUser, User toUser, int type) throws ApiException {
        // ?????????????????????????????????
        if (fromUser.getIdentity() == 0 && toUser.getIdentity() == 0) {
            // ????????????????????????????????????
            return -1;
        }

        // ????????????????????????????????????
        if (fromUser.getIdentity() == 1 && toUser.getIdentity() == 1) {
            // ??????????????????????????????
            return -5;
        }

        BigDecimal callPrice;
        List<Coupon> couponList;
        Asset asset;

        if (toUser.getIdentity() == 1 ) {
            // ???????????????

            // ??????????????????
            if (toUser.getRest() == 1) {
                Date now = new Date();
                if (isTimeBetween(now, toUser.getRestStartDate(), toUser.getRestEndDate())) {
                    // ??????????????????
                    return -4;
                }
            }

            // ????????????????????????????????????
            if ((type == 0 && toUser.getMode() == 2) ||
                    (type == 1 && toUser.getMode() == 1)) {
                // ???????????????????????????
                return -3;
            }

            callPrice = type == 0 ? toUser.getCallPrice() : toUser.getVideoPrice();
            asset = assetRepository.findByUserId(fromUser.getId());
            couponList = couponService.getCallFreeCoupon(fromUser, type);
        } else {
            // ???????????????
            callPrice = type == 0 ? fromUser.getCallPrice() : fromUser.getVideoPrice();
            asset = assetRepository.findByUserId(toUser.getId());
            couponList = couponService.getCallFreeCoupon(toUser, type);
        }

        if (Objects.isNull(callPrice)) {
            return 1;
        }

        for (Coupon coupon :
                couponList) {
            if (couponService.isCouponValid(coupon)) {
                return 1;
            }
        }

        if (asset.getCoin().compareTo(callPrice) >= 0) {
            return 1;
        }

        if (toUser.getIdentity() == 0) {
            // ???????????????????????????????????????
            return -2;
        } else {
            // ?????????????????????????????????????????????
            return 0;
        }
    }

    @Override
    public List<ProfitConsumeDetail> getProfitConsumeList(User user, int pageIndex, int pageSize) throws ApiException {
        Date now = new Date();
        long limit = now.getTime() - 15 * 24 * 3600 * 1000;
        Date limitDay = new Date(limit);
        Pageable pageable = PageRequest.of(pageIndex, pageSize);
        if (user.getIdentity() == 1) {
            return userRepository.getAnchorProfitList(user.getId(), limitDay, pageable).getContent();
        } else {
            return userRepository.getUserConsumeList(user.getId(), limitDay, pageable).getContent();
        }
    }

    @Deprecated
    @SneakyThrows
    @Override
    public User updatePhotosEx(User user, Integer updateType, String existsNames, String coverName,
                               String existsGuards, String guardNames,
                               Map<String, MultipartFile> multipartFileMap) throws ApiException {
        // ??????????????? 1: ?????????????????????2: ?????????????????????
        IdentityAudit audit = null;

        if (updateType == 1) {
            // ?????????????????????

            // ??????????????????????????????
            Optional<IdentityAudit> identityAudit = identityAuditService.getLastPhotosAudit(user);

            if (identityAudit.isPresent() &&
                    identityAudit.get().getStatus() == 10) {
                // ???????????????????????????
                throw new ApiException(-1, "?????????????????????????????????????????????");
            }

            // ????????????????????????????????????????????????
            audit = identityAuditService.createAuditPrepare(user, 12);
        }

        Map<String, UserMaterial> photoMap = new HashMap<>();
        Long userId = user.getId();

        for (Map.Entry<String, MultipartFile> entry: multipartFileMap.entrySet()) {
            String key = entry.getKey();
            MultipartFile file = entry.getValue();

            if (file.isEmpty()) {
                continue;
            }

            String randomUUID = UUID.randomUUID().toString();
            InputStream inputStream = file.getInputStream();
            String filename = file.getOriginalFilename();
            String suffixName = filename.substring(filename.lastIndexOf("."));

            if (!isPictureSupported(suffixName)) {
                throw new ApiException(-1, "?????????????????????!");
            }

            String objectName = String.format("%s%s_%s_%s%s",
                    ossConfProp.getCosUserProfilePhotoPrefix(),
                    CryptoUtils.base64AesSecret(user.getUsername(), ossConfProp.getObjectAesKey()),
                    new Date().getTime(),
                    randomUUID,
                    suffixName);

            cosService.putObject(ossConfProp.getUserDynamicCosBucket(),
                    ossConfProp.getCosUserProfileRootPath() + objectName,
                    inputStream, file.getSize(), "image/png");

            if (!StringUtils.isEmpty(objectName)) {
                // ????????????
                UserMaterial userMaterial = new UserMaterial();
                userMaterial.setUserId(userId);
                userMaterial.setName(objectName);
                if (updateType == 1) {
                    userMaterial.setType(11);
                } else {
                    userMaterial.setType(0);
                }
                photoMap.put(key, userMaterial);
            }
        }

        // ????????????????????????????????????
        if (!StringUtils.isEmpty(guardNames)) {
            List<String> guardNameList = changePhotosToList(guardNames);
            guardNameList.forEach(s -> {
                if (photoMap.containsKey(s)) {
                    photoMap.get(s).setPurpose(1);
                }
            });
        }
        if (!StringUtils.isEmpty(coverName)) {
            if (photoMap.containsKey(coverName)) {
                photoMap.get(coverName).setPurpose(2);
            }
        }

        // ??????????????????
        userMaterialService.addList(photoMap.values());

        // ?????????????????????
        List<UserMaterial> photoMaterialList;
        if (updateType == 1) {
            photoMaterialList = userMaterialService.getAuditPreparePhotos(user);
        } else {
            photoMaterialList = userMaterialService.getPhotos(user);
        }

        if (photoMaterialList.size() > 0) {
            if (!StringUtils.isEmpty(existsNames)) {
                // ?????????????????????
                List<String> existPhotos = changePhotosToList(existsNames);
                List<UserMaterial> needRemovePhotos = photoMaterialList.stream()
                        .filter(userMaterial -> !existPhotos.contains(userMaterial.getName()))
                        .collect(Collectors.toList());

                photoMaterialList.removeAll(needRemovePhotos);

                // ???????????????
                userMaterialService.deleteList(needRemovePhotos);
            }

            if (!StringUtils.isEmpty(existsGuards)) {
                // ?????????????????????
                List<String> existPhotos = changePhotosToList(existsGuards);
                List<UserMaterial> needRemovePhotos = photoMaterialList.stream()
                        .filter(userMaterial -> !existPhotos.contains(userMaterial.getName()))
                        .collect(Collectors.toList());

                photoMaterialList.removeAll(needRemovePhotos);

                // ???????????????
                userMaterialService.deleteList(needRemovePhotos);
            }

            // ??????????????????????????????????????????????????????????????????
            if (!StringUtils.isEmpty(guardNames)) {
                List<String> guardNameList = changePhotosToList(guardNames);
                guardNameList.forEach(s -> {
                    photoMaterialList.stream()
                            .filter(userMaterial -> userMaterial.getName().equals(s))
                            .findFirst()
                            .ifPresent(userMaterial -> {
                                userMaterial.setPurpose(1);
                                userMaterialService.updateList(Collections.singletonList(userMaterial));
                            });
                });
            }
            if (!StringUtils.isEmpty(coverName)) {
                photoMaterialList.stream()
                        .filter(userMaterial -> userMaterial.getName().equals(coverName))
                        .findFirst()
                        .ifPresent(userMaterial -> {
                            userMaterial.setPurpose(2);
                            userMaterialService.updateList(Collections.singletonList(userMaterial));
                        });
            }
        }
        
        if (updateType == 1) {
            audit.setMaterialPhoto(1);
            identityAuditRepository.save(audit);

            return getSelfProfile(user);
        } else {
//            user = userRepository.save(user);

            user.setOriginPhotos(userMaterialService.getPhotos(user));
            return _prepareResponse(user);
        }
    }

    @SneakyThrows
    @Override
    public User updatePhotosEx(User user, Integer updateType, String existsPhotos,
                               String coverName, Map<String, MultipartFile> multipartFileMap) throws ApiException {
        // ??????????????? 1: ?????????????????????2: ????????????????????? 3: ????????????
        IdentityAudit audit = null;

        if (updateType == 1) {
            // ?????????????????????

            // ??????????????????????????????
            Optional<IdentityAudit> identityAudit = identityAuditService.getLastPhotosAudit(user);

            if (identityAudit.isPresent() &&
                    identityAudit.get().getStatus() == 10) {
                // ???????????????????????????
                throw new ApiException(-1, "?????????????????????????????????????????????");
            }

            // ???????????????????????????????????????
            audit = identityAuditService.createAuditPrepare(user, 12);
        }

        List<String> photoList = new ArrayList<>();

        for (Map.Entry<String, MultipartFile> entry: multipartFileMap.entrySet()) {
            String key = entry.getKey();
            MultipartFile file = entry.getValue();

            if (file.isEmpty()) {
                continue;
            }

            String randomUUID = UUID.randomUUID().toString();

            InputStream inputStream = file.getInputStream();
            String filename = file.getOriginalFilename();
            String suffixName = filename.substring(filename.lastIndexOf("."));

            if (!isPictureSupported(suffixName)) {
                throw new ApiException(-1, "?????????????????????!");
            }

            String objectName = String.format("%s%s_%s_%s%s",
                    ossConfProp.getCosUserProfilePhotoPrefix(),
                    CryptoUtils.base64AesSecret(user.getUsername(), ossConfProp.getObjectAesKey()),
                    new Date().getTime(),
                    randomUUID,
                    suffixName);

            cosService.putObject(ossConfProp.getUserDynamicCosBucket(),
                    ossConfProp.getCosUserProfileRootPath() + objectName,
                    inputStream, file.getSize(), "image/png");

            if (!StringUtils.isEmpty(objectName)) {

                if (key.equals(coverName)) {
                    // ?????????????????????????????????
                    photoList.add(0, objectName);
                } else {
                    photoList.add(objectName);
                }
            }
        }

        String originPhotosString;
        if (Objects.nonNull(audit.getPhotos())) {
            originPhotosString = audit.getPhotos();
        } else {
            originPhotosString = user.getPhotos();
        }
        if (Objects.nonNull(originPhotosString) && !StringUtils.isEmpty(existsPhotos)) {
            // ???????????????????????????
            List<String> originPhotos = changePhotosToList(originPhotosString);
            List<String> existPhotos = changePhotosToList(existsPhotos);

            List<String> filterPhotos = originPhotos.stream()
                    .filter(existPhotos::contains)
                    .collect(Collectors.toList());

            if (photoList.size() > 0) {
                filterPhotos.addAll(photoList);
            }

            // ?????????????????????
            if (!StringUtils.isEmpty(coverName)) {
                if (photoList.contains(coverName) &&
                        photoList.indexOf(coverName) != 0) {
                    photoList.remove(coverName);
                    photoList.add(0, coverName);
                }
            }

            user.setPhotos(filterPhotos.toString());
            if (updateType == 1) {
                audit.setPhotos(filterPhotos.toString());
            }
        } else {
            if (photoList.size() > 0) {

                user.setPhotos(photoList.toString());
                if (updateType == 1) {
                    audit.setPhotos(photoList.toString());
                }
            }
        }

        if (updateType == 1) {
            identityAuditRepository.save(audit);
            return getSelfProfile(user);
        } else {
            user = userRepository.save(user);
            return _prepareResponse(user);
        }
    }

    @SneakyThrows
    @Override
    public User updateGuardPhotos(User user, Integer updateType, String existsGuards,
                                  Map<String, MultipartFile> multipartFileMap) throws ApiException {

        IdentityAudit audit = null;

        if (updateType == 1) {
            // ?????????????????????

            // ??????????????????????????????
            Optional<IdentityAudit> identityAudit = identityAuditService.getLastPhotosAudit(user);

            if (identityAudit.isPresent() &&
                    identityAudit.get().getStatus() == 10) {
                // ???????????????????????????
                throw new ApiException(-1, "?????????????????????????????????????????????");
            }

            // ???????????????????????????????????????

            audit = identityAuditService.createAuditPrepare(user, 14);
        }

        List<String> photoList = new ArrayList<>();

        for (MultipartFile file: multipartFileMap.values()) {
            if (file.isEmpty()) {
                continue;
            }

            String randomUUID = UUID.randomUUID().toString();

            InputStream inputStream = file.getInputStream();
            String filename = file.getOriginalFilename();
            String suffixName = filename.substring(filename.lastIndexOf("."));

            if (!isPictureSupported(suffixName)) {
                throw new ApiException(-1, "?????????????????????!");
            }

            String objectName = String.format("%s%s_%s_%s%s",
                    ossConfProp.getCosUserProfilePhotoPrefix(),
                    CryptoUtils.base64AesSecret(user.getUsername(), ossConfProp.getObjectAesKey()),
                    new Date().getTime(),
                    randomUUID,
                    suffixName);

            cosService.putObject(ossConfProp.getUserDynamicCosBucket(),
                    ossConfProp.getCosUserProfileRootPath() + objectName,
                    inputStream, file.getSize(), "image/png");

            if (!StringUtils.isEmpty(objectName)) {
                photoList.add(objectName);
            }
        }

//        if (photoList.size() == 0 ) {
//            throw new ApiException(-1, "??????????????????,???????????????");
//        }

        String originGuardPhotosString;
        if (Objects.nonNull(audit.getGuardPhotos())) {
            originGuardPhotosString = audit.getGuardPhotos();
        } else {
            originGuardPhotosString = user.getGuardPhotos();
        }
        if (Objects.nonNull(originGuardPhotosString) && !StringUtils.isEmpty(existsGuards)) {
            // ???????????????????????????
            List<String> originPhotos = changePhotosToList(originGuardPhotosString);
            List<String> existPhotos = changePhotosToList(existsGuards);

            List<String> filterPhotos = originPhotos.stream()
                    .filter(existPhotos::contains)
                    .collect(Collectors.toList());

            if (photoList.size() > 0) {
                filterPhotos.addAll(photoList);
            }

            user.setGuardPhotos(filterPhotos.toString());
            if (updateType == 1) {
                audit.setGuardPhotos(filterPhotos.toString());
            }
        } else {
            if (photoList.size() > 0) {

                user.setGuardPhotos(photoList.toString());
                if (updateType == 1) {
                    audit.setGuardPhotos(photoList.toString());
                }
            }
        }

        if (updateType == 1) {
            identityAuditRepository.save(audit);
            return getSelfProfile(user);
        } else {
            user = userRepository.save(user);
            return _prepareResponse(user);
        }
    }

    @Deprecated
    @SneakyThrows
    @Override
    public User updateVerifyVideo(User user, Integer code,
                                  Map<String, MultipartFile> multipartFileMap) throws ApiException {

        if (user.getVideoAudit() == 1) {
            throw new ApiException(-1, "?????????????????????????????????????????????");
        }

        for (MultipartFile file: multipartFileMap.values()) {
            if (file.isEmpty()) {
                continue;
            }

            String randomUUID = UUID.randomUUID().toString();

            InputStream inputStream = file.getInputStream();
            String filename = file.getOriginalFilename();
            String suffixName = filename.substring(filename.lastIndexOf("."));

            if (!isVideoSupported(suffixName)) {
                throw new ApiException(-1, "?????????????????????!");
            }

            // ????????????????????????
            String objectName = String.format("%s%s_%s_%s%s",
                    ossConfProp.getCosUserProfileVideoPrefix(),
                    CryptoUtils.base64AesSecret(user.getUsername(), ossConfProp.getObjectAesKey()),
                    new Date().getTime(),
                    randomUUID,
                    suffixName);

            cosService.putObject(ossConfProp.getUserDynamicCosBucket(),
                    ossConfProp.getCosUserProfileRootPath() + objectName,
                    inputStream, file.getSize(), "video/mp4");

            // ????????????
            UserMaterial userMaterial = userMaterialService.getAuditVideo(user);
            if (Objects.nonNull(userMaterial)) {
                userMaterialService.deleteList(Collections.singletonList(userMaterial));
            }

            userMaterial = new UserMaterial();
            userMaterial.setUserId(user.getId());
            userMaterial.setType(2);
            userMaterial.setPurpose(code);
            userMaterialService.addList(Collections.singletonList(userMaterial));

            // ????????????????????????????????????
            return getSelfProfile(user);
        }

        throw new ApiException(-1, "??????????????????");
    }

    @SneakyThrows
    @Override
    public User updateAuditVideo(User user, Integer updateType, Integer code,
                                 Map<String, MultipartFile> multipartFileMap) throws ApiException {

        IdentityAudit audit = null;

        if (updateType == 1) {
            // ?????????????????????

            // ??????????????????????????????
            Optional<IdentityAudit> identityAudit = identityAuditService.getLastAuditVideoAudit(user);

            if (identityAudit.isPresent() &&
                    identityAudit.get().getStatus() == 10) {
                // ???????????????????????????
                throw new ApiException(-1, "?????????????????????????????????????????????");
            }

            // ???????????????????????????????????????
            audit = identityAuditService.createAuditPrepare(user, 16);
        }

        for (MultipartFile file: multipartFileMap.values()) {
            if (file.isEmpty()) {
                continue;
            }

            String randomUUID = UUID.randomUUID().toString();

            InputStream inputStream = file.getInputStream();
            String filename = file.getOriginalFilename();
            String suffixName = filename.substring(filename.lastIndexOf("."));

            if (!isVideoSupported(suffixName)) {
                throw new ApiException(-1, "?????????????????????!");
            }

            // ????????????????????????
            String objectName = String.format("%s%s_%s_%s%s",
                    ossConfProp.getCosUserProfileVideoPrefix(),
                    CryptoUtils.base64AesSecret(user.getUsername(), ossConfProp.getObjectAesKey()),
                    new Date().getTime(),
                    randomUUID,
                    suffixName);

            cosService.putObject(ossConfProp.getUserDynamicCosBucket(),
                    ossConfProp.getCosUserProfileRootPath() + objectName,
                    inputStream, file.getSize(), "video/mp4");

            user.setAuditVideo(objectName);
            user.setVideoAuditCode(code);
            if (updateType == 1) {
                audit.setAuditVideo(objectName);
                audit.setAuditVideoCode(code);
            }

            // ????????????????????????????????????
            break;
        }

        if (user.getAuditVideo() == null || user.getAuditVideo().isEmpty()) {
            throw new ApiException(-1, "?????????????????????");
        }

        if (updateType == 1) {
            identityAuditRepository.save(audit);
            return getSelfProfile(user);
        } else {
            user = userRepository.save(user);
            return _prepareResponse(user);
        }
    }

    @Override
    public List<AnchorCallStatusDto> getAnchorStatusList() throws ApiException {
        // TODO: 2021/7/20 ???????????????
        String thumbnail_tail = "!head"; // 300x300
        Set<String> set=redisTemplate.keys("*");
        List<String> list = new ArrayList<String>(set);
        List<AnchorCallStatusDto> dtoList = new ArrayList<>();
        List<Object> results = redisTemplate.executePipelined(new RedisCallback<String>() {
            @Override
            public String doInRedis(RedisConnection connection) throws DataAccessException {
                for (String s: list) {
                    User user=getUserByDigitId2(s);
                    if(getUserByDigitId2(s)!=null){
                        redisService.get(s);
                        connection.get(s.getBytes(StandardCharsets.UTF_8));
                        AnchorCallStatusDto dto = new AnchorCallStatusDto();
                        if(user.getIdentity()==1){
                            dto.setDigitId(user.getDigitId());

                            String thumbnailUrl = String.format("%s/%s%s%s",
                                    cosService.getDomainName(ossConfProp.getCosCdn(),
                                            ossConfProp.getUserDynamicCosBucket()),
                                    ossConfProp.getCosUserProfileRootPath(), user.getHead(), thumbnail_tail);
                            dto.setStatus(Integer.valueOf(redisService.get(s).toString()));
                            dto.setHeadThumbnailUrl(thumbnailUrl);
                            dtoList.add(dto);
                        }

                    }
                }
                return null;
            }
        });
        return dtoList;
    }

    @Override
    public Map<String, Object> getAnchorStatusLCount(List<String> anchorList) throws ApiException {
        List<Pair<String, String>> pairs = redisService.getList(anchorList);
        Map<String, Object> resultMap = new HashMap<>();
        int i=0;
        for(Pair  pair: pairs){
            if(pair.getSecond().toString().equals("1")){
                i++;
            }
        }
        resultMap.put("onLine_anchor",i);
        return  resultMap;

    }

    // TODO: 2020/12/30 ?????????????????????
    // minio ??????????????????????????????
    @Deprecated
    @SneakyThrows
    public static String minioPutImageAndThumbnail(String bucketName,
                                                                String objectName,
                                                                InputStream inputStream,
                                                                Long size,
                                                                String contentType,
                                                                OssService ossService) throws ApiException {
        BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
        // mark top
        bufferedInputStream.mark(Integer.MAX_VALUE);

        String response = ossService.putObject(bucketName, objectName,
                bufferedInputStream, size, contentType);

        if (response.isEmpty()) {
            throw new ApiException(-1, "?????????????????????!");
        }

        // reset stream to mark position
        bufferedInputStream.reset();

        // ????????????
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        if (size > 1024 * 1024 * 2) {
            // 2m ?????? ?????????????????????????????????????????????0.2
            Thumbnails.of(bufferedInputStream).scale(0.5).outputQuality(0.4).toOutputStream(outputStream);
        } else if (size > 1024 * 1024) {
            // 1m ??? 2m, ????????????????????????0.75???????????????????????????0.2
            Thumbnails.of(bufferedInputStream).scale(0.75).outputQuality(0.4).toOutputStream(outputStream);
        } else if (size > 1024 * 1024 * 0.5) {
            // 500k ~1m
            Thumbnails.of(bufferedInputStream).scale(0.75f).outputQuality(0.6).toOutputStream(outputStream);
        } else if (size > 1024 * 1024 * 0.1) {
            // 100k ~ 500k
            Thumbnails.of(bufferedInputStream).scale(1.0f).outputQuality(0.6).toOutputStream(outputStream);
        } else {
            // 100k ~ 500k
            Thumbnails.of(bufferedInputStream).scale(1.0f).outputQuality(1.0).toOutputStream(outputStream);
        }

        // ??????????????????
        InputStream thumbnailStream = new ByteArrayInputStream(outputStream.toByteArray());
        String thumbnailObjectName = String.format("thumbnail_%s", objectName);
        String responseThumbnail = ossService.putObject(bucketName, thumbnailObjectName,
                thumbnailStream, (long) outputStream.size(), contentType);

        if (responseThumbnail.isEmpty()) {
            throw new ApiException(-1, "?????????????????????!");
        }

        // ????????????????????????response
        return response;
    }

    /**
     * ??????????????????????????????
     */
    private User _prepareResponse(User user) {
        // ?????????????????????????????????
        user.setAge(DateUtils.getAgeFromBirth(user.getBirth()));
        user.setConstellation(DateUtils.getConstellationFromBirth(user.getBirth()));

        // ???????????????????????????????????????????????????
        if (user.getPhotos() != null) {
            List<String> photosNameList = changePhotosToList(user.getPhotos());
            commonService.getUserProfilePhotosCompleteUrl(photosNameList)
                    .ifPresent(v -> {
                        user.setPhotoUrlList(v.getFirst());
                        user.setPhotoThumbnailUrlList(v.getSecond());
                    });
        }

        // ???????????????????????????????????????????????????
        commonService.getUserProfileHeadCompleteUrl(user.getHead())
                .ifPresent(v -> {
                    user.setHeadUrl(v.getFirst());
                    user.setHeadThumbnailUrl(v.getSecond());
                });

        // ????????????????????????????????????
        commonService.getUserProfileVoiceCompleteUrl(user.getVoice())
                .ifPresent(user::setVoiceUrl);

        // ??????????????????????????????????????????????????????
//        if (user.getSelfie() != null && !user.getSelfie().isEmpty()) {
//            String selfieUrl = String.format("%s/%s", ossConfProp.getMinioUrl(), user.getSelfie());
//            String selfieThumbnailUrl = String.format("%s/thumbnail_%s", ossConfProp.getMinioUrl(), user.getSelfie());
//            user.setSelfieUrl(selfieUrl);
//            user.setSelfieThumbnailUrl(selfieThumbnailUrl);
//        }

        // ???????????????????????????
        commonService.getUserProfileVideoCompleteUrl(user.getVideo())
                .ifPresent(user::setVideoUrl);

        if (Objects.nonNull(user.getVideoPrice())) {
            user.setVideoPrice(user.getVideoPrice().setScale(2, BigDecimal.ROUND_HALF_UP));
        } else {
            user.setVideoPrice(BigDecimal.ZERO.setScale(2, BigDecimal.ROUND_HALF_UP));
        }

        if (Objects.nonNull(user.getCallPrice())) {
            user.setCallPrice(user.getCallPrice().setScale(2, BigDecimal.ROUND_HALF_UP));
        } else {
            user.setCallPrice(BigDecimal.ZERO.setScale(2, BigDecimal.ROUND_HALF_UP));
        }

        if (Objects.nonNull(user.getMessagePrice())) {
            user.setMessagePrice(user.getMessagePrice().setScale(2, BigDecimal.ROUND_HALF_UP));
        } else {
            user.setMessagePrice(BigDecimal.ZERO.setScale(2, BigDecimal.ROUND_HALF_UP));
        }

        // ??????????????????
        user.setCurrRest(0);
        if (user.getRest() == 1) {
            Date now = new Date();
            if (isTimeBetween(now, user.getRestStartDate(), user.getRestEndDate())) {
                // ??????????????????
                user.setCurrRest(1);
            }
        }

        // ????????????
        if (user.getGuardPhotos() != null) {
            List<String> photosNameList = changePhotosToList(user.getGuardPhotos());
            commonService.getUserProfilePhotosCompleteUrl(photosNameList)
                    .ifPresent(v -> {
                        user.setGuardPhotosUrlList(v.getFirst());
                        user.setThumbnailGuardPhotosUrlList(v.getSecond());
                    });
        }

        // ????????????
        commonService.getUserProfileVideoCompleteUrl(user.getAuditVideo())
                .ifPresent(user::setVideoAuditUrl);

        // ????????????????????????
//        List<UserMaterial> lastPhotos;
//        if (Objects.nonNull(user.getOriginPhotos())) {
//            // ??????????????????????????????????????????????????????????????????
//            lastPhotos = user.getOriginPhotos();
//        } else {
//            // ???????????????????????????????????????
//            lastPhotos = userMaterialService.getPhotos(user);
//            if (lastPhotos.size() == 0) {
//                // ????????????????????????????????????????????????????????????????????????, ??????????????????????????????????????????????????????????????????????????????
//                if (Objects.nonNull(user.getPhotos())) {
//                    List<String> photosNameList = changePhotosToList(user.getPhotos());
//                    photosNameList.forEach(s -> {
//                        UserMaterial userMaterial = new UserMaterial();
//                        userMaterial.setUserId(user.getId());
//                        userMaterial.setName(s);
//                        lastPhotos.add(userMaterial);
//                    });
//                }
//            }
//        }
//
//        if (lastPhotos.size() > 0) {
//            List<UserMaterial> lastPhotosAnother = ArrayUtils.deepCopy(lastPhotos);
//
//            List<UserMaterial> originPhotos = lastPhotos.stream()
//                    .peek(userMaterial -> userMaterial.setName(String.format("%s/%s/%s",
//                            ossConfProp.getMinioVisitUrl(), ossConfProp.getUserProfileBucket(), userMaterial.getName())))
//                    .collect(Collectors.toList());
//
//            List<UserMaterial> thumbnailPhotos = lastPhotosAnother.stream()
//                    .peek(userMaterial -> userMaterial.setName(String.format("%s/%s/thumbnail_%s",
//                            ossConfProp.getMinioVisitUrl(), ossConfProp.getUserProfileBucket(), userMaterial.getName())))
//                    .collect(Collectors.toList());
//
//            user.setOriginPhotos(originPhotos);
//            user.setThumbnailPhotos(thumbnailPhotos);
//        }
//
//        if (Objects.nonNull(user.getVideoAuditUrl())) {
//            String completeUrl = String.format("%s/%s/%s",
//                    ossConfProp.getMinioVisitUrl(), ossConfProp.getUserProfileBucket(), user.getVideoAuditUrl());
//            user.setVideoAuditUrl(completeUrl);
//        }

        return user;
    }

    /**
     * ???photos??????????????????list
     */
    public static List<String> changePhotosToList(String photos) {
        return Arrays.stream(photos.split("\\[|\\]|,"))
                .filter(s -> !s.isEmpty())
                .map(String::trim)
                .collect(Collectors.toList());
    }

    // ????????????
    // TODO: 2021/4/2 ??????lable service ??????
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

    /**
     * 8??????id??? 1?????????2020-2028????????????1 - 9)??? 4?????????????????????????????? ?????? 366*25???3???????????????????????????????????????
     * ???2020????????????2028?????????????????????????????????????????????id?????????9?????????10??????
     */
    protected static String calcDigitId(LocalDateTime dateTime, long index) {
        // ???2020???????????????
        final int startYear = 2020;
        final int maxIndex = 999;

        int year = dateTime.getYear();
        int yearIndex = year - startYear + 1;

        int dayOfYear = dateTime.getDayOfYear();
        int hour = dateTime.getHour() + 1;
        int hourOfYear = dayOfYear * hour;

        // TODO: 2020/11/9 ????????????????????????
        if (yearIndex <= 0) {
            //
        }

        if (index > maxIndex) {

        }


        if (index > maxIndex) {
            // ???????????????????????????999??????????????????9????????????id??????????????????????????????9999
            return String.format("%01d%04d%04d", yearIndex, hourOfYear, index);
        } else {
            return String.format("%01d%04d%03d", yearIndex, hourOfYear, index);
        }
    }

    protected static String calcDigitId(Date date, long index) {

        GregorianCalendar gregorianCalendar = new GregorianCalendar();
        gregorianCalendar.setTime(date);

        // ???2020???????????????
        final int startYear = 2020;
        final int maxIndex = 999;

        int year = gregorianCalendar.get(Calendar.YEAR);
        int yearIndex = year - startYear + 1;

        int dayOfYear = gregorianCalendar.get(Calendar.DAY_OF_YEAR);
        int hour = gregorianCalendar.get(Calendar.HOUR_OF_DAY) + 1;
//        int am_pm = gregorianCalendar.get(Calendar.AM_PM);
//        if (am_pm == 1) {
//            hour += 12;
//        }
        int hourOfYear = dayOfYear * 24 + hour;

        // TODO: 2020/11/9 ????????????????????????
        if (yearIndex <= 0) {
            //
        }

        if (index > maxIndex) {

        }


        if (index > maxIndex) {
            // ???????????????????????????999??????????????????9????????????id??????????????????????????????9999
            return String.format("%01d%04d%04d", yearIndex, hourOfYear, index);
        } else {
            return String.format("%01d%04d%03d", yearIndex, hourOfYear, index);
        }
    }

    // ???????????????
    protected static String calcDigitIdV2(Date date, long index) {

        GregorianCalendar gregorianCalendar = new GregorianCalendar();
        gregorianCalendar.setTime(date);

        // ???2020???????????????
        final int startYear = 2020;
        final int maxIndex = 9999;

        int year = gregorianCalendar.get(Calendar.YEAR);
        int yearIndex = year - startYear + 1;

        int dayOfYear = gregorianCalendar.get(Calendar.DAY_OF_YEAR);

        // ???????????????????????????,??????????????????
        dayOfYear = 999 - dayOfYear;

        // TODO: 2020/11/9 ????????????????????????
        if (yearIndex <= 0) {
            //
        }

        if (index > maxIndex) {

        }


        if (index > maxIndex) {
            // ???????????????????????????9999??????????????????9????????????id??????????????????????????????99999
            return String.format("%01d%03d%05d", yearIndex, dayOfYear, index);
        } else {
            return String.format("%01d%03d%04d", yearIndex, dayOfYear, index);
        }
    }

    public static boolean isPictureSupported(String stuff) {
        return stuff.toLowerCase().equals(".jpg") ||
                stuff.toLowerCase().equals(".jpeg") ||
                stuff.toLowerCase().equals(".png");
    }

    public static boolean isVideoSupported(String stuff) {
//        return stuff.toLowerCase().equals(".mp4") ||
//                stuff.toLowerCase().equals(".avi");

        return true;
    }

    public static boolean isAudioSupported(String stuff) {
//        return stuff.toLowerCase().equals(".mp3");
        return true;
    }

    // ??????"HH:mm"????????????????????????????????????????????????
    @SneakyThrows
    private static boolean isTimeBetween(Date time, Date startTime, Date endTime) {

        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm");
        Date finalNow = dateFormat.parse(dateFormat.format(time));

        Date dayEnd = dateFormat.parse("23:59");
        Date dayStart = dateFormat.parse("00:00");

        if (Objects.nonNull(startTime) && Objects.nonNull(endTime)) {
            if (startTime.before(endTime)) {
                // ?????????24???
                if ((finalNow.after(startTime) || finalNow.equals(startTime)) &&
                        finalNow.before(endTime)) {
                    return true;
                }
            } else {
                // ??????24???
                if ((finalNow.after(startTime) || finalNow.equals(startTime) && finalNow.before(dayEnd)) ||
                        (finalNow.after(dayStart) || finalNow.equals(dayStart)) && finalNow.before(endTime)) {
                    return true;
                }
            }
        } else {
            return true;
        }

        return false;
    }
}

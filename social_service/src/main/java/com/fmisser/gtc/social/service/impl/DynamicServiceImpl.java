package com.fmisser.gtc.social.service.impl;

import com.fmisser.gtc.base.aop.ReTry;
import com.fmisser.gtc.base.dto.social.DynamicCommentDto;
import com.fmisser.gtc.base.dto.social.DynamicDto;
import com.fmisser.gtc.base.exception.ApiException;
import com.fmisser.gtc.base.prop.OssConfProp;
import com.fmisser.gtc.base.utils.CryptoUtils;
import com.fmisser.gtc.base.utils.DateUtils;
import com.fmisser.gtc.social.domain.Dynamic;
import com.fmisser.gtc.social.domain.DynamicComment;
import com.fmisser.gtc.social.domain.DynamicHeart;
import com.fmisser.gtc.social.domain.User;
import com.fmisser.gtc.social.repository.DynamicCommentRepository;
import com.fmisser.gtc.social.repository.DynamicHeartRepository;
import com.fmisser.gtc.social.repository.DynamicRepository;
import com.fmisser.gtc.social.service.DynamicService;
import com.fmisser.gtc.social.service.ImCallbackService;
import com.fmisser.gtc.social.service.SysConfigService;
import com.fmisser.gtc.social.utils.MinioUtils;
import io.minio.ObjectWriteResponse;
import lombok.SneakyThrows;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static com.fmisser.gtc.social.service.impl.UserServiceImpl.*;

@Service
public class DynamicServiceImpl implements DynamicService {
    private final DynamicRepository dynamicRepository;
    private final DynamicHeartRepository dynamicHeartRepository;
    private final DynamicCommentRepository dynamicCommentRepository;
    private final OssConfProp ossConfProp;
    private final MinioUtils minioUtils;
    private final ImCallbackService imCallbackService;
    private final SysConfigService sysConfigService;

    public DynamicServiceImpl(DynamicRepository dynamicRepository,
                              DynamicCommentRepository dynamicCommentRepository,
                              DynamicHeartRepository dynamicHeartRepository,
                              OssConfProp ossConfProp,
                              MinioUtils minioUtils,
                              ImCallbackService imCallbackService,
                              SysConfigService sysConfigService) {
        this.dynamicRepository = dynamicRepository;
        this.dynamicHeartRepository = dynamicHeartRepository;
        this.dynamicCommentRepository = dynamicCommentRepository;
        this.ossConfProp = ossConfProp;
        this.minioUtils = minioUtils;
        this.imCallbackService = imCallbackService;
        this.sysConfigService = sysConfigService;
    }

    @Override
    @SneakyThrows
    public DynamicDto create(User user, int type, String content, String city,
                             BigDecimal longitude, BigDecimal latitude,
                          Map<String, MultipartFile> multipartFileMap) throws ApiException {

        int ret = imCallbackService.textModeration(user.getDigitId(), content);
        if (ret == 0) {
            throw new ApiException(-1, "发现违规内容，发表失败");
        }

        Dynamic dynamic = new Dynamic();
        dynamic.setUserId(user.getId());
        dynamic.setType(type);
        dynamic.setContent(content);
        // 直接设置通过
        dynamic.setStatus(10);

//        int ret = imCallbackService.textModeration(user.getDigitId(), content);
//        if (ret == 0) {
//            // 机审失败
//            dynamic.setStatus(1);
//            dynamic.setMessage("机审不通过");
//        } else {
//            // 通过
//            dynamic.setStatus(10);
//        }

        if (Objects.nonNull(city)) {
            dynamic.setCity(city);
        } else {
            // 如果没有传city，则使用信息所在城市
            dynamic.setCity(user.getCity());
        }

        if (Objects.nonNull(longitude) && Objects.nonNull(latitude)) {
            dynamic.setLongitude(longitude);
            dynamic.setLatitude(latitude);
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

            if (type == 1) {
                // 图片
                if (!isPictureSupported(suffixName)) {
                    throw new ApiException(-1, "图片格式不支持!");
                }

                String objectName = String.format("%s%s_%s_%s%s",
                        ossConfProp.getUserDynamicPicturePrefix(),
                        CryptoUtils.base64AesSecret(user.getUsername(), ossConfProp.getObjectAesKey()),
                        new Date().getTime(),
                        randomUUID,
                        suffixName);

                ObjectWriteResponse response = minioPutImageAndThumbnail(ossConfProp.getUserDynamicBucket(),
                        objectName,
                        inputStream,
                        file.getSize(),
                        "image/png",
                        minioUtils);

                if (!StringUtils.isEmpty(response.object())) {
                    photoList.add(response.object());
                }

            } else if (type == 2) {
                // 视频
                if (!isVideoSupported(suffixName)) {
                    throw new ApiException(-1, "视频格式不支持!");
                }

                // 视频暂不提供压缩
                String objectName = String.format("%s%s_%s_%s%s",
                        ossConfProp.getUserDynamicVideoPrefix(),
                        CryptoUtils.base64AesSecret(user.getUsername(), ossConfProp.getObjectAesKey()),
                        new Date().getTime(),
                        randomUUID,
                        suffixName);

                ObjectWriteResponse response = minioUtils.put(ossConfProp.getUserDynamicBucket(), objectName,
                        inputStream, file.getSize(), "video/mp4");

                dynamic.setVideo(response.object());

                // 只处理第一个能处理的视频
                break;
            }
        }

        if (type == 1) {
            // 图片
            if (photoList.size() == 0 ) {
                throw new ApiException(-1, "上传信息出错,请稍后重试");
            }

            dynamic.setPictures(photoList.toString());
        } else if (type == 2) {
            if (StringUtils.isEmpty(dynamic.getVideo())) {
                throw new ApiException(-1, "上传信息不正确");
            }
        }

        dynamic = dynamicRepository.save(dynamic);
        return _prepareDynamicResponse(dynamic, user);
    }

    @Override
    public List<DynamicDto> getUserDynamicList(User user, User selfUser, int pageIndex, int pageSize, String version) throws ApiException {
        // 如果不提供自己的 user id 则默认设置为0
        Long selfUserId = 0L;
        if (selfUser != null) {
            selfUserId = selfUser.getId();
        }

        // 审核控制
        Date dateLimit = sysConfigService.getAppAuditDynamicDateLimit(version);

        List<Integer> status;
        if (selfUserId.equals(user.getId())) {
            // 自己可以看见审核中的动态
            status = Arrays.asList(1, 10);
        } else {
            status = Collections.singletonList(10);
        }

        Pageable pageable = PageRequest.of(pageIndex, pageSize);
        Page<DynamicDto> dynamicDtos = dynamicRepository
                .getUserDynamicList(user.getId(), selfUserId, status, dateLimit, pageable);

//        long totalEle = dynamicDtos.getTotalElements();
//        System.out.println(totalEle);
//        long totalPage = dynamicDtos.getTotalPages();
//        System.out.println(totalPage);

        return _prepareDynamicDtoResponse(dynamicDtos.getContent());
    }

    @Override
//    @ReTry(value = {PessimisticLockingFailureException.class})
    public int dynamicHeart(Long dynamicId, User selfUser, int isCancel) throws ApiException {
        Optional<Dynamic> optionalDynamic = dynamicRepository.findById(dynamicId);
        if (!optionalDynamic.isPresent()) {
            throw new ApiException(-1, "动态数据不存在或已删除！");
        }
        Dynamic dynamic = optionalDynamic.get();

        DynamicHeart dynamicHeart;
        Optional<DynamicHeart> optionalDynamicHeart =
                dynamicHeartRepository.findByDynamicIdAndUserId(dynamicId, selfUser.getId());

        if (optionalDynamicHeart.isPresent()) {
            dynamicHeart = optionalDynamicHeart.get();

            if (dynamicHeart.getIsCancel() == isCancel) {
                throw new ApiException(-1, "已经点赞或取消，请勿重复操作！");
            }

            dynamicHeart.setIsCancel(isCancel);
        } else {

            if (isCancel == 1) {
                throw new ApiException(-1, "没有点赞记录无法取消！");
            }

            dynamicHeart = new DynamicHeart();
            dynamicHeart.setUserId(selfUser.getId());
            dynamicHeart.setDynamicId(dynamic.getId());
        }

        dynamicHeartRepository.save(dynamicHeart);

        return 1;
    }

    @Override
//    @ReTry(value = {PessimisticLockingFailureException.class})
    public int newDynamicComment(Long dynamicId, Long commentIdTo, User selfUser, User toUser, String content) throws ApiException {
        Optional<Dynamic> optionalDynamic = dynamicRepository.findById(dynamicId);
        if (!optionalDynamic.isPresent()) {
            throw new ApiException(-1, "动态数据不存在或已删除！");
        }

        int ret = imCallbackService.textModeration(selfUser.getDigitId(), content);
        if (ret == 0) {
            throw new ApiException(-1, "发现违规内容，发表失败");
        }

        Dynamic dynamic = optionalDynamic.get();

        DynamicComment dynamicComment = new DynamicComment();
        dynamicComment.setDynamicId(dynamic.getId());
        dynamicComment.setUserIdFrom(selfUser.getId());
        // 判断是不是回复某个人
        if (Objects.nonNull(commentIdTo) && Objects.nonNull(toUser)) {
            dynamicComment.setCommentIdTo(commentIdTo);
            dynamicComment.setUserIdTo(toUser.getId());
        }
        dynamicComment.setContent(content);

        dynamicCommentRepository.save(dynamicComment);

        return 1;
    }

    @Override
    @ReTry(value = {PessimisticLockingFailureException.class})
    public int delDynamicComment(Long commentId, User selfUser) throws ApiException {
        Optional<DynamicComment> optionalDynamicComment = dynamicCommentRepository.findById(commentId);
        if (!optionalDynamicComment.isPresent()) {
            throw new ApiException(-1, "评论数据不存在或已删除！");
        }

        DynamicComment dynamicComment = optionalDynamicComment.get();
        if (!dynamicComment.getUserIdFrom().equals(selfUser.getId()) ) {
            throw new ApiException(-1, "无权限删除评论！");
        }

        if (dynamicComment.getIsDelete() == 1) {
            throw new ApiException(-1, "请勿重复删除！");
        }

        dynamicComment.setIsDelete(1);
        dynamicCommentRepository.save(dynamicComment);

        return 1;
    }

    @Override
    public List<DynamicCommentDto> getDynamicCommentList(Long dynamicId, User selfUser,
                                                         int pageIndex, int pageSize) throws ApiException {
        // 如果不提供自己的 user id 则默认设置为0
        Long selfUserId = 0L;
        if (selfUser != null) {
            selfUserId = selfUser.getId();
        }

        Pageable pageable = PageRequest.of(pageIndex, pageSize);
        List<DynamicCommentDto> dynamicCommentDtos = dynamicCommentRepository
                .getCommentList(dynamicId, pageable)
                .getContent();

        return _prepareDynamicCommentDtoResponse(dynamicCommentDtos, selfUserId);
    }

    @Override
    public List<DynamicDto> getLatestDynamicList(User selfUser, int pageIndex, int pageSize, String version) throws ApiException {
        // 如果不提供自己的 user id 则默认设置为0
        Long selfUserId = 0L;
        if (selfUser != null) {
            selfUserId = selfUser.getId();
        }

        // 审核控制
        Date dateLimit = sysConfigService.getAppAuditDynamicDateLimit(version);

        Pageable pageable = PageRequest.of(pageIndex, pageSize);
        List<DynamicDto> dynamicDtos = dynamicRepository
                .getLatestDynamicList(selfUserId, dateLimit, pageable).getContent();
        return _prepareDynamicDtoResponse(dynamicDtos);
    }

    @Override
    public List<DynamicDto> getFollowLatestDynamicList(User selfUser, int pageIndex, int pageSize, String version) throws ApiException {
        Pageable pageable = PageRequest.of(pageIndex, pageSize);

        // 审核控制
        Date dateLimit = sysConfigService.getAppAuditDynamicDateLimit(version);

        List<DynamicDto> dynamicDtos = dynamicRepository
                .getDynamicListByFollow(selfUser.getId(), dateLimit, pageable).getContent();
        return _prepareDynamicDtoResponse(dynamicDtos);
    }

    @Override
    public int delete(User user, Long dynamicId) throws ApiException {
        Optional<Dynamic> dynamicOptional = dynamicRepository.findById(dynamicId);
        if (!dynamicOptional.isPresent()) {
            throw new ApiException(-1, "动态不存在，无法删除!");
        }

        Dynamic dynamic = dynamicOptional.get();
        if (!dynamic.getUserId().equals(user.getId())) {
            throw new ApiException(-1, "您没有权限这样操作!");
        }

        if (dynamic.getIsDelete() == 1) {
            throw new ApiException(-1, "动态不存在,无法删除!");
        }

        dynamic.setIsDelete(1);
        dynamicRepository.save(dynamic);

        return 1;
    }

    @Override
    public Pair<List<DynamicDto>, Map<String, Object>> managerListDynamic(String digitId, String nick, String content,
                                   Date startTime, Date endTime,
                                   int pageIndex, int pageSize) throws ApiException {
        Pageable pageable = PageRequest.of(pageIndex - 1, pageSize);
        Page<DynamicDto> dynamicDtoPage =
                dynamicRepository.getManagerDynamicList(digitId, nick, content, startTime, endTime, pageable);

        Map<String, Object> extra = new HashMap<>();
        extra.put("totalPage", dynamicDtoPage.getTotalPages());
        extra.put("totalEle", dynamicDtoPage.getTotalElements());
        extra.put("currPage", pageIndex);

        return Pair.of(dynamicDtoPage.getContent(), extra);
    }

    @Override
    public int deleteDynamic(Long dynamicId) throws ApiException {
        Optional<Dynamic> dynamicOptional = dynamicRepository.findById(dynamicId);
        if (!dynamicOptional.isPresent()) {
            throw new ApiException(-1, "动态不存在，无法删除!");
        }

        Dynamic dynamic = dynamicOptional.get();

        if (dynamic.getIsDelete() == 1) {
            throw new ApiException(-1, "动态不存在,无法删除!");
        }

        dynamic.setIsDelete(1);
        dynamicRepository.save(dynamic);

        return 1;
    }

    @Override
    public int auditDynamic(Long dynamicId, int pass, String message) throws ApiException {
        Optional<Dynamic> dynamicOptional = dynamicRepository.findById(dynamicId);
        if (!dynamicOptional.isPresent()) {
            throw new ApiException(-1, "动态不存在，无法操作!");
        }

        Dynamic dynamic = dynamicOptional.get();
        if (!StringUtils.isEmpty(message)) {
            dynamic.setMessage(message);
        }

        if (pass == 1) {
            dynamic.setStatus(10);
        } else {
            dynamic.setStatus(20);
        }
        dynamicRepository.save(dynamic);

        return 1;
    }

    @Override
    public List<DynamicDto> compat(List<DynamicDto> dynamicDtoList, String version) throws ApiException {
        final String compatVersion = "v212";
        if (version.compareTo(compatVersion) < 0) {
            // 兼容以前的动态
            return dynamicDtoList.stream()
                    .peek(dynamicDto -> {
                        dynamicDto.setLatitude(null);
                        dynamicDto.setLongitude(null);
                    }).collect(Collectors.toList());
        } else {
            return dynamicDtoList;
        }
    }

    private List<DynamicCommentDto> _prepareDynamicCommentDtoResponse(List<DynamicCommentDto> dynamicCommentDtos, Long selfUserId) {
        for (DynamicCommentDto commentDto :
                dynamicCommentDtos) {

            if (commentDto.getUserIdFrom().equals(selfUserId)) {
                commentDto.setIsSelf(1L);
            } else {
                commentDto.setIsSelf(0L);
            }

            if (!StringUtils.isEmpty(commentDto.getHead())) {
                String headUrl = String.format("%s/%s/%s",
                        ossConfProp.getMinioVisitUrl(),
                        ossConfProp.getUserProfileBucket(),
                        commentDto.getHead());
                String headThumbnailUrl = String.format("%s/%s/thumbnail_%s",
                        ossConfProp.getMinioVisitUrl(),
                        ossConfProp.getUserProfileBucket(),
                        commentDto.getHead());
                commentDto.setHeadUrl(headUrl);
                commentDto.setHeadThumbnailUrl(headThumbnailUrl);
            }
        }

        return dynamicCommentDtos;
    }

    private List<DynamicDto> _prepareDynamicDtoResponse(List<DynamicDto> dynamicDtos) {
        for (DynamicDto dynamicDto:
                dynamicDtos) {

            // 提供完整的图片url
            if (!StringUtils.isEmpty(dynamicDto.getPictures())) {
                List<String> pictureNameList = changePhotosToList(dynamicDto.getPictures());
                List<String> pictureUrlList = pictureNameList.stream()
                        .map( name -> String.format("%s/%s/%s",
                                ossConfProp.getMinioVisitUrl(), ossConfProp.getUserDynamicBucket(), name))
                        .collect(Collectors.toList());
                List<String> pictureThumbnailUrlList = pictureNameList.stream()
                        .map( name -> String.format("%s/%s/thumbnail_%s",
                                ossConfProp.getMinioVisitUrl(), ossConfProp.getUserDynamicBucket(), name))
                        .collect(Collectors.toList());
                dynamicDto.setPictureUrlList(pictureUrlList);
                dynamicDto.setPictureThumbnailUrlList(pictureThumbnailUrlList);
            }

            // 提供完整的视频链接
            if (!StringUtils.isEmpty(dynamicDto.getVideo())) {
                String videoUrl = String.format("%s/%s/%s",
                        ossConfProp.getMinioVisitUrl(),
                        ossConfProp.getUserDynamicBucket(),
                        dynamicDto.getVideo());
                dynamicDto.setVideoUrl(videoUrl);
            }

            // 完善个人信息数据

            if (dynamicDto.getBirth() != null) {
                // 通过生日计算年龄和星座
                dynamicDto.setAge(DateUtils.getAgeFromBirth(dynamicDto.getBirth()));
                dynamicDto.setConstellation(DateUtils.getConstellationFromBirth(dynamicDto.getBirth()));
            }

            if (!StringUtils.isEmpty(dynamicDto.getHead())) {
                String headUrl = String.format("%s/%s/%s",
                        ossConfProp.getMinioVisitUrl(),
                        ossConfProp.getUserProfileBucket(),
                        dynamicDto.getHead());
                String headThumbnailUrl = String.format("%s/%s/thumbnail_%s",
                        ossConfProp.getMinioVisitUrl(),
                        ossConfProp.getUserProfileBucket(),
                        dynamicDto.getHead());
                dynamicDto.setHeadUrl(headUrl);
                dynamicDto.setHeadThumbnailUrl(headThumbnailUrl);
            }
        }

        return dynamicDtos;
    }

    private DynamicDto _prepareDynamicResponse(Dynamic dynamic, User user) {
        DynamicDto dynamicDto = new DynamicDto(
                dynamic.getId(), dynamic.getUserId(), user.getDigitId(),
                dynamic.getContent(), dynamic.getCity(), dynamic.getType(),
                dynamic.getVideo(), dynamic.getPictures(),
                dynamic.getCreateTime(), dynamic.getModifyTime(),
                dynamic.getLongitude(), dynamic.getLatitude(),
                0L, 0L, 0L,
                user.getNick(), user.getBirth(), user.getGender(), user.getHead());
        List<DynamicDto> dynamicDtos = new ArrayList<>(1);
        dynamicDtos.add(dynamicDto);
        return _prepareDynamicDtoResponse(dynamicDtos).get(0);
    }

}

package com.fmisser.gtc.social.service;

import com.fmisser.gtc.base.exception.ApiException;
import com.fmisser.gtc.social.domain.IdentityAudit;
import com.fmisser.gtc.social.domain.User;

import java.util.List;
import java.util.Optional;

/**
 * 用户身份审核
 */

public interface IdentityAuditService {
    // 获取最近的用户资料的审核，可能为空，表示未进行过审核
    Optional<IdentityAudit> getLastProfileAudit(User user) throws ApiException;

    // 获取最近的照片资料的审核，可能为空，表示未进行过审核
    Optional<IdentityAudit> getLastPhotosAudit(User user) throws ApiException;

    // 获取最近的视频资料的审核，可能为空，表示未进行过审核
    Optional<IdentityAudit> getLastVideoAudit(User user) throws ApiException;

    Optional<IdentityAudit> getLastGuardPhotosAudit(User user) throws ApiException;

    Optional<IdentityAudit> getLastGuardVideoAudit(User user) throws ApiException;

    Optional<IdentityAudit> getLastAuditVideoAudit(User user) throws ApiException;

    // 请求身份认证审核 type = 0 不需要资料都完善 type = 1需要资料都完善
    // mode 是 0表示有待审核的都提交 1 2 3 4 5 6 分别表示单独提交资料 照片 视频 守护照片 守护视频 认证视频
    int requestIdentityAudit(User user, int type, int mode) throws ApiException;

    // 守护版本新的审核
    int requestIdentityAuditEx(User user, int type, int mode) throws ApiException;

    // 一次性获得最近的一次审核数据
    List<IdentityAudit> getLatestAuditAllType(User user) throws ApiException;

    // 响应身份认证审核结果
    // 由满足权限的审核角色操作
    int responseAudit(Long auditId, int pass, String message) throws ApiException;

    Optional<IdentityAudit> getLastProfilePrepare(User user) throws ApiException;

    Optional<IdentityAudit> getLastPhotosPrepare(User user) throws ApiException;

    Optional<IdentityAudit> getLastVideoPrepare(User user) throws ApiException;

    Optional<IdentityAudit> getLastGuardPhotosPrepare(User user) throws ApiException;

    Optional<IdentityAudit> getLastGuardVideoPrepare(User user) throws ApiException;

    Optional<IdentityAudit> getLastAuditVideoPrepare(User user) throws ApiException;

    Optional<IdentityAudit> getLastAuditPrepare(Long userId, int type) throws ApiException;

    IdentityAudit createAuditPrepare(User user, int type) throws ApiException;


}

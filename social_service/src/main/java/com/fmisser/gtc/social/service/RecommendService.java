package com.fmisser.gtc.social.service;

import com.fmisser.gtc.base.dto.social.RecommendAnchorDto;
import com.fmisser.gtc.base.dto.social.RecommendDto;
import com.fmisser.gtc.base.exception.ApiException;
import com.fmisser.gtc.social.domain.User;

import java.text.ParseException;
import java.util.List;

public interface RecommendService {
    // 获取随机私聊推荐主播
    List<RecommendAnchorDto> getRandRecommendAnchorList(Integer count, int gender) throws ApiException;

    // 获取通话推荐主播
    List<RecommendAnchorDto> getRandRecommendAnchorListForCall(Integer count) throws ApiException;
}

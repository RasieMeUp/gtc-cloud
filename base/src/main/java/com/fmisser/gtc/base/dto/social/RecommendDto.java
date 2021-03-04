package com.fmisser.gtc.base.dto.social;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Date;

/**
 * 推荐主播数据
 */

public interface RecommendDto {
    Long getLevel();
    int getType();
    String getDigitId();
    String getNick();
    String getPhone();
    int getGender();
    @JsonFormat(timezone = "GMT+8", pattern = "HH:mm:ss")
    Date getStartTime();
    @JsonFormat(timezone = "GMT+8", pattern = "HH:mm:ss")
    Date getEndTime();
}

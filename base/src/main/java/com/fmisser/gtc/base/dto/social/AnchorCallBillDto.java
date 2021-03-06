package com.fmisser.gtc.base.dto.social;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 主播通话收益结构
 */

public interface AnchorCallBillDto {
    String getDigitId();
    String getNick();
    BigDecimal getProfit();
    int getDuration();
    int getCard();
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    Date getCreateTime();
}

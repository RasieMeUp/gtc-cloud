package com.fmisser.gtc.social.controller;

import com.fmisser.gtc.base.response.ApiResp;
import com.fmisser.gtc.social.domain.Coupon;
import com.fmisser.gtc.social.domain.User;
import com.fmisser.gtc.social.service.CouponService;
import com.fmisser.gtc.social.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import org.hibernate.validator.constraints.Range;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Api(description = "优惠券API")
@RestController
@RequestMapping("/coupon")
@Validated
public class CouponController {
    @Autowired
    private CouponService couponService;

    @Autowired
    private UserService userService;

    @ApiOperation(value = "优惠券列表")
    @ApiImplicitParam(name = "Authorization", value = "Bearer Token", required = true, dataType = "String", paramType = "header")
    @GetMapping("/list")
    ApiResp<List<Coupon>> getCouponList() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getPrincipal().toString();
        User userDo = userService.getUserByUsername(username);

        List<Coupon> couponList = couponService.getCouponList(userDo);
        return ApiResp.succeed(couponList);
    }

    @ApiOperation(value = "优惠券奖励")
    @ApiImplicitParam(name = "Authorization", value = "Bearer Token", required = true, dataType = "String", paramType = "header")
    @GetMapping("/award")
    ApiResp<List<Coupon>> getCouponAward(@RequestParam("type") @Range(max = 1) Integer type) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getPrincipal().toString();
        User userDo = userService.getUserByUsername(username);

        if (type == 0) {
            // 注册
            return ApiResp.succeed(couponService.getRegAward(userDo));
        } else if (type == 1) {
            // 首充
            return ApiResp.succeed(couponService.getFirstRechargeAward(userDo));
        } else {
            return ApiResp.succeed(null);
        }
    }
}

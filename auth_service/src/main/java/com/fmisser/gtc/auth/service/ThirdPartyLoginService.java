package com.fmisser.gtc.auth.service;

import com.fmisser.gtc.base.exception.ApiException;

/**
 * 第三方登录
 */

public interface ThirdPartyLoginService {

    // 苹果登录验证
    // https://developer.apple.com/documentation/sign_in_with_apple/sign_in_with_apple_rest_api/authenticating_users_with_sign_in_with_apple
    boolean checkAppleIdentityToken(String identityToken, String subject) throws ApiException;
}

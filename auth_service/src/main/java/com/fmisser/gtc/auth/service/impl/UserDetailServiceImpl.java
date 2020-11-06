package com.fmisser.gtc.auth.service.impl;

import com.fmisser.gtc.auth.domain.Role;
import com.fmisser.gtc.auth.domain.User;
import com.fmisser.gtc.auth.feign.OAuthService;
import com.fmisser.gtc.auth.repository.RoleRepository;
import com.fmisser.gtc.auth.repository.UserRepository;
import com.fmisser.gtc.auth.service.AutoLoginService;
import com.fmisser.gtc.auth.service.SmsService;
import com.fmisser.gtc.auth.service.UserService;
import com.fmisser.gtc.base.dto.auth.TokenDto;
import com.fmisser.gtc.base.exception.ApiException;
import com.fmisser.gtc.base.response.ApiResp;
import com.fmisser.gtc.base.utils.AuthUtils;
import com.fmisser.gtc.base.utils.JPushUtils;
import io.swagger.annotations.Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import sun.tools.jstat.Token;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service("top")
public class UserDetailServiceImpl implements UserService {
    private final Logger logger = LoggerFactory.getLogger(UserDetailServiceImpl.class);

    private final UserRepository userRepository;

    private final RoleRepository roleRepository;

    private final PasswordEncoder passwordEncoder;

    private final SmsService smsService;

    private final AutoLoginService autoLoginService;

    private final OAuthService oAuthService;

    public UserDetailServiceImpl(UserRepository userRepository,
                                 RoleRepository roleRepository,
                                 PasswordEncoder passwordEncoder,
                                 SmsService smsService,
                                 AutoLoginService autoLoginService,
                                 OAuthService oAuthService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.smsService = smsService;
        this.autoLoginService = autoLoginService;
        this.oAuthService = oAuthService;
    }

    @Override
    public UserDetails loadUserByUsername(String s) throws UsernameNotFoundException {
        return userRepository.findByUsername(s);
    }

    @Override
    public User create(String username, String password) {
        Optional<User> userOptional = Optional.ofNullable(userRepository.findByUsername(username));
        userOptional.ifPresent(u -> {
            throw new IllegalArgumentException("user already exists:" + u.getUsername());
        });

        User user = new User();
        user.setId(0L);
        user.setUsername(username);
        String encodePwd = passwordEncoder.encode(password);
        user.setPassword(encodePwd);

        // give default role: USER_ROLE
        user.setAuthorities(_innerCreateUserRole());

        User savedUser = userRepository.save(user);
        logger.info("new user has been created: {}", savedUser.getUsername());
        return savedUser;
    }

    @Override
    public ApiResp<TokenDto> autoLogin(String phone, String token) throws ApiException {
        String basicAuth = AuthUtils.genBasicAuthString("test-client", "test-client-secret");
        TokenDto dto = oAuthService.autoLogin(basicAuth, phone, token, "test", "auto_login");
        return ApiResp.succeed(dto);
    }

    @Override
    public ApiResp<TokenDto> smsLogin(String phone, String code) throws ApiException {
        String basicAuth = AuthUtils.genBasicAuthString("test-client", "test-client-secret");
        TokenDto dto = oAuthService.autoLogin(basicAuth, phone, code, "test", "sms_login");
        return ApiResp.succeed(dto);
    }

    @Override
    public ApiResp<TokenDto> login(String username, String password) throws ApiException {
        String basicAuth = AuthUtils.genBasicAuthString("test-client", "test-client-secret");
        TokenDto dto = oAuthService.login(basicAuth, username, password, "test", "password");
        return ApiResp.succeed(dto);
    }

    /**
     * 自定义手机号验证码登录模式
     * 注册，登录一体
     */
    public UserDetails loadUserByPhoneAndSms(String phone, String code) {
        // verify sms code
        if (!smsService.checkPhoneCode(phone, code, 0)) {
            return null;
        }

        User user = userRepository.findByUsername(phone);
        if (user != null) {
            return user;
        }

        // 认证过后，如果数据库没有这条数据，则创建一条
        return _innerCreateByPhone(phone, 1);
    }

    /**
     * 自定义手机号一键登录模式
     * 注册，登录一体
     */
    public UserDetails loadUserByPhoneAuto(String phone, String token) {
        // verify phone token
        if (!autoLoginService.checkPhoneToken(phone, token)) {
            return null;
        }

        User user = userRepository.findByUsername(phone);
        if (user != null) {
            return user;
        }

        // 认证过后，如果数据库没有这条数据，则创建一条
        return _innerCreateByPhone(phone, 2);
    }

    private User _innerCreateByPhone(String phone, int type) {
        User newUser = new User();
        newUser.setId(0L);  // 因为存在多对多，必须要设置个值
        newUser.setUsername(phone);
        newUser.setType(1); // 手机号类型
        String encodePwd = passwordEncoder.encode(new Date().toString());   // 随机创建一个密码
        newUser.setPassword(encodePwd);

        // give default role: USER_ROLE
        newUser.setAuthorities(_innerCreateUserRole());

        User savedUser = userRepository.save(newUser);
        logger.info("new user has been created: {}", savedUser.getUsername());
        return savedUser;
    }

    private List<Role> _innerCreateUserRole() {
        Role userRole = roleRepository.getUserRole();
        List<Role> roleList = new ArrayList<>();
        roleList.add(userRole);

        return roleList;
    }
}

package com.fmisser.gtc.social.config;

import com.fmisser.fpp.cache.redis.service.RedisService;
import com.fmisser.gtc.base.exception.ApiException;
import com.fmisser.gtc.base.exception.oauth2.ForbiddenAccountAccessDeniedException;
import com.fmisser.gtc.social.domain.DeviceForbidden;
import com.fmisser.gtc.social.domain.Forbidden;
import com.fmisser.gtc.social.domain.User;
import com.fmisser.gtc.social.domain.UserDevice;
import com.fmisser.gtc.social.repository.UserDeviceRepository;
import com.fmisser.gtc.social.service.DeviceForbiddenService;
import com.fmisser.gtc.social.service.ForbiddenService;
import com.fmisser.gtc.social.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.access.SecurityConfig;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

@Configuration
public class CommonAccessDecisionManager implements AccessDecisionManager {

    @Autowired
    private ForbiddenService forbiddenService;

    @Autowired
    private DeviceForbiddenService deviceForbiddenService;

    @Autowired
    private UserService userService;
    @Autowired
    private RedisService redisService;
    @Autowired
    private  UserDeviceRepository userDeviceRepository;

    @Override
    public void decide(Authentication authentication, Object object, Collection<ConfigAttribute> configAttributes) throws AccessDeniedException, InsufficientAuthenticationException, ApiException {

        String username = authentication.getPrincipal().toString();
        // ??????????????????????????????????????? anonymousUser
        if (username.equals("anonymousUser")) {
            return;
        }

        if (authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_USER"))) {
            // ???????????????????????????
            if (userService.isUserExist(username)) {
                User user = userService.getUserByUsername(username);
                Forbidden forbidden = forbiddenService.getUserForbidden(user);
                if (forbidden != null) {
                    if (forbidden.getDays() < 0) {
                        throw new ForbiddenAccountAccessDeniedException("????????????????????????????????????");
                    } else if (forbidden.getDays() == 0) {
                        throw new ForbiddenAccountAccessDeniedException("???????????????????????????????????????????????????????????????");
                    } else {
                        SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("MM???dd???HH:mm");
                        String msg = String.format("???????????????????????????????????????????????????%d????????????%s??????",
                                forbidden.getDays(), dateTimeFormatter.format(forbidden.getEndTime()));
                        throw new ForbiddenAccountAccessDeniedException(msg);
                    }
                }
                List<DeviceForbidden> list=deviceForbiddenService.findByUserId(user.getId());
                if(!list.isEmpty()){
                    for(DeviceForbidden e:list){
                        if(e.getType()==1){
                            Optional<UserDevice> optionalUserDevice=   userDeviceRepository.findById(e.getDeviceId());
                            if(optionalUserDevice.isPresent()){
                                String redisKey=e.getDeviceId()+":"+e.getUserId()+":"+optionalUserDevice.get().getDeviceAndroidId();
                                if(redisService.hasKey(redisKey)){
                                   /* SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("MM???dd???HH:mm");
                                    String msg = String.format("???????????????????????????????????????????????????%d????????????%s??????",
                                            e.getDays(), dateTimeFormatter.format(e.getEndTime()));*/
                                    throw new ForbiddenAccountAccessDeniedException("?????????????????????");
                                }
                            }
                        }
                        if(e.getType()==2){
                            String redisKey=e.getDeviceId()+":"+user.getId()+":"+e.getIp();
                            if(redisService.hasKey(redisKey)){
                                throw new ForbiddenAccountAccessDeniedException("?????????????????????");
                            }
                        }
                    }

                }

            }
        }
    }

    @Override
    public boolean supports(ConfigAttribute attribute) {
        return true;
    }

    @Override
    public boolean supports(Class<?> clazz) {
        return true;
    }
}

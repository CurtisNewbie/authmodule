package com.curtisnewbie.module.auth.config;

import com.curtisnewbie.module.messaging.service.MessagingService;
import com.curtisnewbie.service.auth.messaging.routing.RoutingEnum;
import com.curtisnewbie.service.auth.remote.vo.AccessLogInfoVo;
import com.curtisnewbie.service.auth.remote.vo.UserVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author yongjie.zhuang
 */
@Component
public class AuthenticationSuccessHandlerDelegate implements AuthenticationSuccessHandler {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationSuccessHandlerDelegate.class);
    //    @DubboReference(lazy = true)
//    private RemoteAccessLogService accessLogService;
    @Autowired(required = false)
    private AuthenticationSuccessHandlerExtender extender;

    @Autowired
    private MessagingService messagingService;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @PostConstruct
    void postConstruct() {
        if (extender != null) {
            logger.info("Detected extender, will invoke {}'s implementation", extender.getClass().getName());
        }
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
                                        Authentication authentication) throws IOException, ServletException {
        logAccessInfoAsync(httpServletRequest, authentication);
        if (extender != null) {
            extender.onAuthenticationSuccess(httpServletRequest, httpServletResponse, authentication);
        }
    }

    private void logAccessInfoAsync(HttpServletRequest request, Authentication auth) {
        AccessLogInfoVo accessLog = new AccessLogInfoVo();
        accessLog.setIpAddress(request.getRemoteAddr());
        accessLog.setAccessTime(new Date());
        if (auth.getPrincipal() != null && auth.getPrincipal() instanceof UserVo) {
            UserVo user = (UserVo) auth.getPrincipal();
            accessLog.setUserId(user.getId());
            accessLog.setUsername(user.getUsername());
        }

        try {
            logger.info("Logging sign-in info, ip: {}, username: {}, userId: {}",
                    accessLog.getIpAddress(),
                    accessLog.getUsername(),
                    accessLog.getUserId());
            messagingService.sendJson(accessLog, RoutingEnum.SAVE_ACCESS_LOG_ROUTING.getExchange(),
                    RoutingEnum.SAVE_ACCESS_LOG_ROUTING.getRoutingKey());
        } catch (Exception e) {
            logger.error("Unable to save access-log", e);
        }

//        executorService.execute(decorate(() -> {
//            try {
//                logger.info("Logging sign-in info, ip: {}, username: {}, userId: {}",
//                        accessLog.getIpAddress(),
//                        accessLog.getUsername(),
//                        accessLog.getUserId());
//                accessLogService.save(accessLog);
//            } catch (Exception e) {
//                logger.error("Unable to save access-log", e);
//            }
//        }));
    }
}


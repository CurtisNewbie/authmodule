package com.curtisnewbie.module.auth.aop;

import com.curtisnewbie.module.auth.util.AuthUtil;
import com.curtisnewbie.service.auth.remote.api.RemoteOperateLogService;
import com.curtisnewbie.service.auth.remote.exception.InvalidAuthenticationException;
import com.curtisnewbie.service.auth.remote.vo.OperateLogVo;
import com.curtisnewbie.service.auth.remote.vo.UserVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Date;
import java.util.concurrent.CompletableFuture;

import static com.curtisnewbie.module.tracing.common.TracingRunnableDecorator.decorate;

/**
 * Advice for saving operate_log
 * <p>
 * This should be used in combination with {@link LogOperation}
 * </p>
 * <p>
 * You can also disable this functionality by setting {@link #ENABLE_OPERATE_LOG_KEY} to false in *.properties file.
 * </p>
 *
 * @author yongjie.zhuang
 */
@Slf4j
@Aspect
@Component
public class OperateLogAdvice {

    private static final String ANONYMOUS_NAME = "anonymous";
    private static final int ANONYMOUS_ID = 0;
    private static final int MAX_PARAM_LENGTH = 950;
    private static final String ENABLE_OPERATE_LOG_KEY = "auth-module.enable-operate-log";

    @Value("${" + ENABLE_OPERATE_LOG_KEY + ":true}")
    private boolean operateLogEnabled;

    @DubboReference
    private RemoteOperateLogService remoteOperateLogService;

    @PostConstruct
    void onInit() {
        if (!operateLogEnabled)
            log.info("Operation log disabled, configure '{}=true' to turn it on", ENABLE_OPERATE_LOG_KEY);
    }

    @Around("@annotation(logOperation)")
    public Object logOperation(ProceedingJoinPoint pjp, LogOperation logOperation) throws Throwable {
        try {
            if (operateLogEnabled)
                doAsyncOperationLog(pjp, logOperation);
        } catch (Exception e) {
            log.error("Unable to log operation", e);
        }
        return pjp.proceed();
    }

    private void doAsyncOperationLog(ProceedingJoinPoint pjp, LogOperation logOperation) throws InvalidAuthenticationException {
        if (logOperation == null || !logOperation.enabled())
            return;

        OperateLogVo v = new OperateLogVo();
        v.setOperateName(logOperation.name());
        v.setOperateDesc(logOperation.description());
        v.setOperateTime(new Date());
        v.setOperateParam(toParamString(pjp.getArgs()));

        String username = ANONYMOUS_NAME;
        int userId = ANONYMOUS_ID;
        if (AuthUtil.isPrincipalPresent(UserVo.class)) {
            UserVo uv = AuthUtil.getUser();
            username = uv.getUsername();
            userId = uv.getId();
        }
        v.setUsername(username);
        v.setUserId(userId);

        try {
            CompletableFuture.runAsync(decorate(() -> {
                remoteOperateLogService.saveOperateLogInfo(v);
            })).handle((t, e) -> {
                if (e != null)
                    log.error("Unable to save operation log", e);
                return t;
            });
        } catch (Exception e) {
            // catch all exception such that the business operations are never interrupted
            log.error("Unable to save operation log", e);
        }
    }

    private String toParamString(Object[] args) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            if (a == null) {
                continue;
            }
            // skip the injected object from spring
            if (isExcluded(a))
                continue;

            if (i > 0 && sb.length() > 0)
                sb.append(",");
            sb.append(stripOffClassName(a));
        }

        // cut it if necessary
        if (sb.length() > MAX_PARAM_LENGTH) {
            sb.setLength(MAX_PARAM_LENGTH);
            sb.append("...");
        }
        sb.append("]");
        return sb.toString();
    }

    private String stripOffClassName(Object o) {
        String ts = o.toString();
        String clzName = o.getClass().getCanonicalName();
        int i;
        // Object#toString() with outer class's name
        if ((i = ts.indexOf(clzName)) != -1)
            return o.getClass().getSimpleName() + ts.substring(i + clzName.length());

        // Object#toString() with inner class's name
        int j;
        if ((j = ts.lastIndexOf("$")) != -1) {
            // replace the $ with .
            String rts = ts.substring(0, j) + "." + ts.substring(j + 1);
            if ((i = rts.indexOf(clzName)) != -1)
                return o.getClass().getSimpleName() + rts.substring(i + clzName.length());
        }
        return ts;
    }

    private boolean isExcluded(Object o) {
        return o.getClass().getCanonicalName().startsWith("org.springframework");
    }

}

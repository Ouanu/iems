package dev.ouanu.iems.aop;

import java.util.UUID;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Aspect
@Component
public class ServiceLogAspect {
    private static final Logger log = LoggerFactory.getLogger(ServiceLogAspect.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // @Pointcut("execution(* dev.ouanu.iems.service.*.*(..))")
    // public void serviceLogPointcut() {
    //     // Pointcut for service layer
    // }

    @Pointcut("@annotation(dev.ouanu.iems.annotation.ActionLog)")
    public void serviceLogPointcut() {
        // Pointcut for service layer
    }

    @Around("serviceLogPointcut()")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String id = UUID.randomUUID().toString();
        String methodName = joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        try {
            log.info("ID: {}, 方法: {}, 参数: {}", id, methodName, objectMapper.writeValueAsString(args));
        } catch (JsonProcessingException e) {
            log.warn("ID: {}, 方法: {}, 参数序列化失败: {}", id, methodName, args);
        }

        // 执行目标方法
        Object result = joinPoint.proceed();

        long executionTime = System.currentTimeMillis() - startTime;
        log.info("ID: {}, 方法: {} 执行完毕, 耗时: {} ms", id, methodName, executionTime);

        return result;
    }
}

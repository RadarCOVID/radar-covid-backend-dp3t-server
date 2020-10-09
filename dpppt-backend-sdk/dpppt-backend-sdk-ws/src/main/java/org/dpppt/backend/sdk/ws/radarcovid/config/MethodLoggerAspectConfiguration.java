/*
 * Copyright (c) 2020 Gobierno de España
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package org.dpppt.backend.sdk.ws.radarcovid.config;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Aspecto encargado de trazar la información de los servicios implementados.
 */
@Configuration
@ConditionalOnProperty(name = "application.log.enabled", havingValue = "true", matchIfMissing = true)
public class MethodLoggerAspectConfiguration {

    private static final Logger log = LoggerFactory.getLogger("org.dpppt.backend.sdk.ws.radarcovid.annotation.Loggable");

    @Aspect
    @Component
    public class MethodLoggerAspect {
        @Before("execution(@org.dpppt.backend.sdk.ws.radarcovid.annotation.Loggable * *..business.impl..*(..))")
        public void logBefore(JoinPoint joinPoint) {
            if (log.isDebugEnabled()) {
                log.debug("   ************************* INIT SERVICE ******************************");
                log.debug("   Service : Entering in Method : {}", joinPoint.getSignature().getDeclaringTypeName());
                log.debug("   Service : Method :             {}", joinPoint.getSignature().getName());
                log.debug("   Service : Arguments :          {}", Arrays.toString(joinPoint.getArgs()));
                log.debug("   Service : Target class :       {}", joinPoint.getTarget().getClass().getName());
            }
        }

        @AfterThrowing(pointcut = "execution(@org.dpppt.backend.sdk.ws.radarcovid.annotation.Loggable * *..business.impl..*(..))", throwing = "exception")
        public void logAfterThrowing(JoinPoint joinPoint, Throwable exception) {
            log.error("   Service :  An exception has been thrown in {} ()", joinPoint.getSignature().getName());
            log.error("   Service :  Cause : {}", exception.getCause());
            log.error("   Service :  Message : {}", exception.getMessage());
            log.debug("   ************************** END SERVICE ******************************");
        }

        @Around("execution(@org.dpppt.backend.sdk.ws.radarcovid.annotation.Loggable * *..business.impl..*(..))")
        public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {

            long start = System.currentTimeMillis();
            try {
                String className = joinPoint.getSignature().getDeclaringTypeName();
                String methodName = joinPoint.getSignature().getName();
                Object result = joinPoint.proceed();
                long elapsedTime = System.currentTimeMillis() - start;
                log.debug("   Service :  {}.{} () execution time: {} ms", className, methodName, elapsedTime);
                log.debug("   ************************** END SERVICE ******************************");
                return result;

            } catch (IllegalArgumentException e) {
                log.error("   Service :  Illegal argument {} in {}()", Arrays.toString(joinPoint.getArgs()),
                          joinPoint.getSignature().getName(), e);
                log.debug("   ************************** END SERVICE ******************************");
                throw e;
            }
        }
    }

}

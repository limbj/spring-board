package io.github.limbj.board.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 서비스 메서드 실행 시간 로그
 */
@Aspect
@Slf4j
@Component
public class LogAdvice {

	@Around("execution(* io.github.limbj.board.service..*(..))")
	public Object logExecutionTime(ProceedingJoinPoint pjp) throws Throwable {

		long start = System.currentTimeMillis();

		try {
			return pjp.proceed();
		} finally {
			log.debug(pjp.getSignature().toShortString() + " " + (System.currentTimeMillis() - start) + "ms");
		}
	}
}

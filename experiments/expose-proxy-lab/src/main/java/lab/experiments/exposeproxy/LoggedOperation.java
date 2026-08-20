package lab.experiments.exposeproxy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// @Transactional/@Cacheable처럼, 마커 애노테이션 + @Aspect 포인트컷 조합으로 어드바이스를
// 적용하는 실제 선언적 스타일을 재현하기 위한 용도.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LoggedOperation {
}

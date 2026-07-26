package lab.ext.timing;

import java.lang.reflect.Method;

import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.StaticMethodMatcherPointcut;

// JDK 프록시 경로에서는 method가 인터페이스 메서드(예: OrderService#placeOrder, 애노테이션이
// 여기 붙어 있음)로 들어오고, CGLIB 경로에서는 인터페이스가 없으니 구현 클래스의 메서드
// (예: LegacyReport#generate)가 그대로 들어온다 - 두 경우를 하나의 Pointcut으로 다루려면
// AopUtils.getMostSpecificMethod()로 "실제 구현 메서드"까지 함께 확인해야 한다.
final class MeasureTimeAnnotationPointcut extends StaticMethodMatcherPointcut {

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        Method mostSpecificMethod = AopUtils.getMostSpecificMethod(method, targetClass);
        return mostSpecificMethod.isAnnotationPresent(MeasureTime.class) || method.isAnnotationPresent(MeasureTime.class);
    }
}

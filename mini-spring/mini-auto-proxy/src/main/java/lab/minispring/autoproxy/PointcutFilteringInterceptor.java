package lab.minispring.autoproxy;

import lab.minispring.aop.MethodInterceptor;
import lab.minispring.aop.MethodInvocation;

// mini-aop(11주차)의 MiniProxyFactory는 인터셉터 목록을 모든 메서드 호출에 무조건 적용한다 -
// "이 메서드에만 적용"이라는 개념 자체가 없다. 실제 Spring은 ProxyFactory가 프록시를 만들
// 때(getInterceptorsAndDynamicInterceptionAdvice) Advisor의 Pointcut으로 미리 걸러서 애초에
// 매칭 안 되는 메서드의 인터셉터 체인에는 넣지도 않는다. mini-aop 자체를 건드리지 않고 같은
// 효과를 내기 위해, 이 클래스가 "Pointcut이 매칭하면 advice를 실행하고, 아니면 그냥
// proceed()로 건너뛴다"는 필터링을 인터셉터 하나로 감싸서 구현한다.
public final class PointcutFilteringInterceptor implements MethodInterceptor {

    private final MiniAdvisor advisor;

    public PointcutFilteringInterceptor(MiniAdvisor advisor) {
        this.advisor = advisor;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        if (advisor.getPointcut().matches(invocation.getMethod())) {
            return advisor.getAdvice().invoke(invocation);
        }
        return invocation.proceed();
    }
}

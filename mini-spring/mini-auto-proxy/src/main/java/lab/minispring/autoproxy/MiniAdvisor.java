package lab.minispring.autoproxy;

import lab.minispring.aop.MethodInterceptor;

// Pointcut(어디에 적용할지)과 Advice(무엇을 할지)를 하나로 묶는 조합 객체 - 이 둘을 분리해
// 두면 같은 MethodInterceptor를 다른 Pointcut과 자유롭게 조합할 수 있고, MiniAutoProxyCreator는
// "이 빈이 프록시 대상인가"를 Advice 내용을 몰라도 Pointcut만으로 판단할 수 있다.
public final class MiniAdvisor {

    private final MiniPointcut pointcut;
    private final MethodInterceptor advice;

    public MiniAdvisor(MiniPointcut pointcut, MethodInterceptor advice) {
        this.pointcut = pointcut;
        this.advice = advice;
    }

    public MiniPointcut getPointcut() {
        return pointcut;
    }

    public MethodInterceptor getAdvice() {
        return advice;
    }
}

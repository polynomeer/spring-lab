package lab.sampleapp.orderplatform.web;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import lab.sampleapp.orderplatform.aop.CurrentActor;
import lab.sampleapp.orderplatform.aop.Role;

/**
 * HTTP 헤더(X-Member-Id/X-Member-Role)를 CurrentActor.Actor로 해석해서 컨트롤러
 * 파라미터에 바인딩하는 동시에, 같은 값을 {@link CurrentActor}(Phase 2 AOP의
 * AuditAspect/AuthorizationAspect가 참조하는 ThreadLocal)에도 심어 둔다 - 실제 Spring
 * Security의 SecurityContextHolder는 DispatcherServlet보다 앞선 Filter가 채워 두지만,
 * 여기서는 그 필터 계층을 새로 만들지 않고 ArgumentResolver 시점에 대신 채운다. 그 결과
 * "이 리졸버가 실행되는 시점 이전"(예: HandlerInterceptor#preHandle)에는 아직
 * CurrentActor가 비어 있다는 제약이 생긴다 - 의도적으로 받아들인 단순화다.
 */
@Component
public class CurrentMemberArgumentResolver implements HandlerMethodArgumentResolver {

    private final CurrentActor currentActor;

    public CurrentMemberArgumentResolver(CurrentActor currentActor) {
        this.currentActor = currentActor;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentMember.class)
                && CurrentActor.Actor.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String memberId = webRequest.getHeader("X-Member-Id");
        String roleHeader = webRequest.getHeader("X-Member-Role");

        if (memberId == null) {
            CurrentMember annotation = parameter.getParameterAnnotation(CurrentMember.class);
            if (annotation != null && annotation.required()) {
                throw new MissingCurrentMemberException();
            }
            return null;
        }

        Role role = roleHeader != null ? Role.valueOf(roleHeader) : Role.CUSTOMER;
        CurrentActor.Actor actor = new CurrentActor.Actor(memberId, role);
        currentActor.set(actor);
        return actor;
    }
}

package lab.sampleapp.orderplatform.web;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lab.sampleapp.orderplatform.aop.CurrentActor;

/**
 * CurrentActor는 ThreadLocal이고, 실제 서블릿 컨테이너는 요청마다 스레드를 새로 만들지 않고
 * 스레드 풀의 스레드를 재사용한다 - 이 요청이 CurrentActor를 세팅해 놓고 지우지 않으면,
 * 같은 스레드가 재사용되는 다음 요청이 (자신은 인증 헤더를 보내지 않았는데도) 이전 요청의
 * 액터를 그대로 보게 되는 정보 유출/오동작이 생길 수 있다. afterCompletion은 컨트롤러
 * 실행과 뷰 렌더링이 전부 끝난 뒤, 예외가 던져졌든 아니든 항상 호출되므로 정리하기에
 * 가장 안전한 지점이다.
 */
@Component
public class CurrentActorClearingInterceptor implements HandlerInterceptor {

    private final CurrentActor currentActor;

    public CurrentActorClearingInterceptor(CurrentActor currentActor) {
        this.currentActor = currentActor;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        currentActor.clear();
    }
}

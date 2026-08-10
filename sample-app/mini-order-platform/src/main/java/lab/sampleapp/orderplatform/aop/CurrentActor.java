package lab.sampleapp.orderplatform.aop;

import org.springframework.stereotype.Component;

/**
 * "지금 요청을 보낸 사람이 누구인가"를 흉내 낸 최소한의 홀더. 실제 Spring Security의
 * SecurityContextHolder(ThreadLocal 기반)를 그대로 재현하지는 않고, 이 캡스톤 안에서
 * AuthorizationAspect/AuditAspect가 참조할 "현재 액터"만 표현한다 - MVC 단계(Phase 4)에서
 * HTTP 요청과 연결하기 전까지는 테스트가 직접 set()/clear()로 제어한다.
 */
@Component
public class CurrentActor {

    private final ThreadLocal<Actor> holder = new ThreadLocal<>();

    public record Actor(String id, Role role) {
    }

    public void set(Actor actor) {
        holder.set(actor);
    }

    public void clear() {
        holder.remove();
    }

    public Actor get() {
        Actor actor = holder.get();
        if (actor == null) {
            throw new IllegalStateException("no CurrentActor set for this thread");
        }
        return actor;
    }
}

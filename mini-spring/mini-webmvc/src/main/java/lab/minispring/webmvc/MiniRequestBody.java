package lab.minispring.webmvc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 대상 파라미터 타입으로 바인딩 방식을 가른다: String이면 본문을 그대로, record면
// MiniJsonReader로 역직렬화한다 - 실제 Spring이 HttpMessageConverter 목록에서 타입에 맞는
// 컨버터를 고르는 지점과 같다. Jackson 없이 record 전용으로 축소했을 뿐 (중첩 record까지는
// 지원, 리스트/맵/제네릭은 지원하지 않음) - 실제 Jackson 기반 역직렬화는 이번 주 real-Spring
// 실험(project 25/26)에서 이미 확인했다.
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface MiniRequestBody {
}

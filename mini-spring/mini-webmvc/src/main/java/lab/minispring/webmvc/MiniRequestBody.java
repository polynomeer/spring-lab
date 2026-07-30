package lab.minispring.webmvc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 실제 Spring과 달리 JSON 역직렬화는 하지 않는다 - 요청 본문을 문자열 그대로 바인딩한다
// (Jackson 같은 외부 라이브러리 없이도 "본문을 한 번 읽어서 인자로 넘긴다"는 핵심 메커니즘은
// 그대로 보여줄 수 있다. 실제 JSON 변환은 이번 주 real-Spring 실험(project 25/26)에서 이미
// Jackson으로 확인했다).
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface MiniRequestBody {
}

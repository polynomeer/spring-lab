package lab.experiments.lookup;

import org.springframework.stereotype.Component;

// LookupTicketSeller와 똑같이 추상 클래스지만 @Lookup 메서드가 하나도 없다 - 대조군.
// isCandidateComponent()의 "abstract && hasAnnotatedMethods(Lookup.class)" 조건을
// 만족하지 못하므로 컴포넌트 스캔 후보에서 아예 제외돼야 한다.
@Component
public abstract class PlainAbstractNoLookup {

    public abstract String whatever();
}

export interface DocLink {
  path: string;
  section: string;
  excerpt: string;
}

/**
 * semantic 이벤트 타입 → 그 판별 규칙을 실제로 검증한 docs/<NN>-<topic> 문서의 관련 문단.
 * 설계 문서(docs/plan/03-learning-dashboard-design.md) 3.3절이 말하는 "대시보드가 기존
 * 학습 문서를 살아있게 만드는 창구"가 되는 지점 - 여기 인용된 문장은 전부 그 문서에서
 * 그대로 옮긴 것이다(지어내지 않았다).
 */
export const DOC_LINKS: Record<string, DocLink> = {
  SINGLETON_FACTORY_REGISTERED: {
    path: "docs/10-primary-qualifier-circular/primary-qualifier-circular.md",
    section: "6. 호출 흐름",
    excerpt:
      "createBean(A) → 원본 인스턴스 생성 → addSingletonFactory(A, () -> getEarlyBeanReference(A, mbd, 원본)) — 이 시점에는 아직 프록시할지조차 결정되지 않는다. 3차 캐시는 그 결정을 나중으로 미루는 지연 평가 지점이다.",
  },
  PROPERTY_INJECTION_STARTED: {
    path: "docs/10-primary-qualifier-circular/primary-qualifier-circular.md",
    section: "6. 호출 흐름",
    excerpt: "setter/필드 주입은 '인스턴스는 이미 있고 값만 나중에 채운다'는 시점 분리가 가능하다 — 그래서 populateBean() 도중 다른 빈의 조기 참조를 요청할 수 있다.",
  },
  EARLY_REFERENCE_REQUESTED: {
    path: "docs/10-primary-qualifier-circular/primary-qualifier-circular.md",
    section: "11. Spring 설계 의도",
    excerpt:
      "만약 조기 참조로 원본을 노출하고 나중에 postProcessAfterInitialization에서 다시 프록시로 감싸면, 이미 원본을 주입받은 다른 빈은 영원히 프록시가 아닌 원본을 들고 있게 된다.",
  },
  EARLY_REFERENCE_PROXIED: {
    path: "docs/10-primary-qualifier-circular/primary-qualifier-circular.md",
    section: "11. Spring 설계 의도",
    excerpt:
      "getEarlyBeanReference가 '이 빈이 프록시 대상이라면, 조기 노출 시점에 이미 최종 형태(프록시)로 노출한다'고 앞당겨 결정한다. 조기 참조와 최종 인스턴스의 동일성은 우연이 아니라 이 설계가 의도적으로 보장하는 것이다.",
  },
  ADVISOR_LOOKUP_STARTED: {
    path: "docs/12-auto-proxy-creator/auto-proxy-creator.md",
    section: "7.1 jdi-tracer 세션 — 자동 프록시 생성 경로 실측",
    excerpt: "canApply(advisor, ...) → canApply(pointcut, ...) 순으로 평가해 매칭이 없으면 createProxy 히트 없이 곧장 다음 빈으로 진행한다.",
  },
  ADVISOR_EAGERLY_INSTANTIATED: {
    path: "docs/12-auto-proxy-creator/auto-proxy-creator.md",
    section: "7.1 jdi-tracer 세션 — 자동 프록시 생성 경로 실측",
    excerpt:
      "findAdvisorBeans 내부에서 아직 없는 advisor 빈을 getBean()으로 조회하는 순간, 그 빈이 그 자리에서 전체 빈 생성 파이프라인을 거쳐 즉시 인스턴스화된다 — 바깥쪽 findAdvisorBeans가 아직 리턴하지 않은 채로 중첩되어 있다.",
  },
  PROXY_CREATED: {
    path: "docs/12-auto-proxy-creator/auto-proxy-creator.md",
    section: "7.1 jdi-tracer 세션 — 자동 프록시 생성 경로 실측",
    excerpt:
      "같은 DefaultAopProxyFactory#createAopProxy에서 멈췄지만 그 뒤의 결과는 갈린다 — 인터페이스가 없으면 CGLIB, 있으면 JDK 프록시. 두 빈이 정확히 같은 코드 경로를 타다가 이 지점에서만 갈린다.",
  },
  PROXY_SKIPPED: {
    path: "docs/12-auto-proxy-creator/auto-proxy-creator.md",
    section: "7.1 jdi-tracer 세션 — 자동 프록시 생성 경로 실측",
    excerpt: "canApply 평가 결과가 false면 getAdvicesAndAdvisorsForBean이 DO_NOT_PROXY를 반환하고, createProxy 호출 자체가 생략된 채 다음 빈으로 넘어간다.",
  },
  TX_STARTED: {
    path: "docs/14-transaction-propagation/transaction-propagation.md",
    section: "6. 호출 흐름",
    excerpt: "createTransactionIfNecessary()는 매 @Transactional 메서드 진입마다 호출된다 — 이게 새 트랜잭션을 만들지 기존에 참여할지는 그 안의 getTransaction()이 판단한다.",
  },
  TX_SUSPENDED: {
    path: "docs/14-transaction-propagation/transaction-propagation.md",
    section: "7.1 jdi-tracer 세션 — 인터셉터 체인 실측",
    excerpt:
      "suspend(null)이 '아무 트랜잭션도 없을 때' 매번 불린다 — REQUIRED/REQUIRES_NEW/NESTED로 완전히 새 트랜잭션을 만들 때조차 호출된다. 진짜 suspend는 handleExistingTransaction()의 REQUIRES_NEW 분기에서 실제 transaction 객체를 인자로 다시 호출될 때다.",
  },
  TX_RESUMED: {
    path: "docs/14-transaction-propagation/transaction-propagation.md",
    section: "7.1 jdi-tracer 세션 — 인터셉터 체인 실측",
    excerpt:
      "resume()은 바깥 코드가 제어권을 돌려받기도 전에, 안쪽 트랜잭션의 커밋 안에서 이미 끝난다 — payRequiresNew()가 커밋되는 그 순간(processCommit의 정리 단계)에 곧바로 바깥 트랜잭션이 복원된다.",
  },
  TX_COMMITTED: {
    path: "docs/14-transaction-propagation/transaction-propagation.md",
    section: "7.1 jdi-tracer 세션 — 인터셉터 체인 실측",
    excerpt: "REQUIRED 참여자의 실패는 setRollbackOnly()가 아니라 실제 rollback() 호출로 이어진다 — processRollback이 내부적으로 '이 트랜잭션이 진짜 새 트랜잭션인가'를 보고 실제 DB 롤백 여부를 가른다.",
  },
  TX_ROLLED_BACK: {
    path: "docs/14-transaction-propagation/transaction-propagation.md",
    section: "7.1 jdi-tracer 세션 — 인터셉터 체인 실측",
    excerpt:
      "바깥의 commit() 호출이 내부적으로 processCommit이 아니라 processRollback으로 새는 지점이 UnexpectedRollbackException의 진짜 발생 위치다 — 예외를 잡았으니 안전하다는 직관이 트랜잭션 경계에서는 성립하지 않는다.",
  },
  EVENT_PUBLISHED: {
    path: "docs/21-application-events/application-events.md",
    section: "6. 호출 흐름",
    excerpt: "publishEvent(event) → multicastEvent(event, type) → getApplicationListeners(event, type) - 등록된 리스너 중 타입이 맞는 것만, order로 정렬 → 리스너마다 invokeListener() - 발행자 스레드에서 순서대로, 즉시.",
  },
  MULTICAST_STARTED: {
    path: "docs/21-application-events/application-events.md",
    section: "3. 예상 동작 (소스를 보기 전에 작성)",
    excerpt: "리스너 하나가 예외를 던지면 그 리스너만 실패하고 나머지는 계속 실행될 거라 예상했다 - 틀렸다. 기본 errorHandler가 없으면 예외가 그대로 던져지고, multicastEvent()의 반복문 자체가 그 자리에서 멈춘다 - 이후 순서의 리스너는 아예 호출되지 않는다.",
  },
  LISTENER_INVOKED: {
    path: "docs/21-application-events/application-events.md",
    section: "3. 예상 동작 (소스를 보기 전에 작성)",
    excerpt: "리스너 하나가 예외를 던지면 그 리스너만 실패하고 나머지는 계속 실행될 거라 예상했다 - 틀렸다. 기본 errorHandler가 없으면 예외가 그대로 던져지고, multicastEvent()의 반복문 자체가 그 자리에서 멈춘다 - 이후 순서의 리스너는 아예 호출되지 않는다.",
  },
  ASYNC_LISTENER_EXECUTING: {
    path: "docs/21-application-events/application-events.md",
    section: "6. 호출 흐름",
    excerpt:
      "분기 자체가 invokeListener() 바깥이라, invokeListener()에 브레이크포인트를 걸면 동기든 비동기든 항상 발행자 스레드에서 호출된 것으로 보인다 - 실제 스레드 전환은 그 프록시(AsyncExecutionInterceptor)를 통과하는 더 안쪽, listener.onApplicationEvent()가 실제 대상 메서드를 리플렉션으로 호출하는 지점에서 일어난다.",
  },
  TX_LISTENER_EVENT_RECEIVED: {
    path: "docs/21-application-events/application-events.md",
    section: "6. 호출 흐름",
    excerpt:
      "publishEvent(event) → 즉시 실행되지 않는다 → TransactionSynchronizationManager.isSynchronizationActive() 확인 - false(트랜잭션 없음) && !fallbackExecution → 이벤트 버려짐 / true → registerSynchronization(afterCommit 콜백)만 등록, 리턴.",
  },
  TX_LISTENER_INVOKED: {
    path: "docs/21-application-events/application-events.md",
    section: "7. 브레이크포인트",
    excerpt:
      "TransactionalApplicationListenerMethodAdapter에는 processEventWithCallback이라는 메서드가 없다 - 실제 지연 실행은 커밋/롤백 시점에 AbstractPlatformTransactionManager가 호출하는 TransactionalApplicationListenerSynchronization$PlatformSynchronization#afterCompletion → processEventWithCallbacks(복수형)에서 일어난다.",
  },
};

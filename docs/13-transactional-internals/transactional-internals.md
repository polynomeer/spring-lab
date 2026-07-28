# @Transactional 내부 동작 — 결국 11~12주차의 인터셉터 체인이다

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md) 13주차, [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md) 프로젝트 21(Transaction Propagation Playground, 기본 동작)·프로젝트 22(Mini Transaction Manager, 1~2단계)에 대응하는 분석 문서다.

## 1. 이번 질문

- `@Transactional` 메서드가 정상 반환하면 커밋되고, 예외가 나면 롤백된다는 건 알고 있다 — 그런데 **어떤** 예외가 롤백을 유발하는가? 기본 규칙은 무엇이고, 어떻게 바꾸는가?
- `readOnly`/`timeout` 같은 속성은 실제로 무엇을 하는가?
- self-invocation과 `private` 메서드에 붙은 `@Transactional`은 왜 조용히 무시되는가 — 11·12주차에서 순수 AOP 레벨/`MethodTimingBeanPostProcessor` 레벨로 이미 확인한 것이, `@Transactional`이라는 실무에서 가장 자주 쓰이는 어드바이스에서도 정확히 같은 이유로 똑같이 재현되는가?

## 2. 공식 문서 요약

- Spring 레퍼런스 매뉴얼(Transaction Management, "Rolling Back a Declarative Transaction")은 기본 롤백 규칙을 명시한다 — **unchecked 예외(`RuntimeException`, `Error`)는 롤백, checked 예외는 커밋**. EJB의 관례를 따른 것이라고 설명한다. `rollbackFor`/`noRollbackFor`로 이 기본 규칙을 뒤집을 수 있다.
- 같은 장, "Using `@Transactional`"의 self-invocation 절: "프록시 모드(기본값)에서는 프록시를 통해 들어오는 외부 호출만 가로챌 수 있다 — 즉 같은 클래스 내부의 self-invocation은, 대상 메서드에 실제로 `@Transactional`이 붙어 있어도 트랜잭션을 시작하지 않는다"고 명시한다. 11·12주차에서 다룬 것과 같은 근본 원인이다.
- `readOnly`에 대해서는 "힌트(hint)"라는 표현을 명확히 쓴다 — 트랜잭션 매니저와 드라이버가 이 힌트를 실제로 어떻게 쓸지는 구현에 달렸다고 밝히고 있다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- checked 예외도 "예외가 났으니 당연히 롤백"될 거라 예상했다 — **틀렸다.** 기본 규칙은 `RuntimeException`/`Error`만 롤백하고, checked 예외(`java.io.IOException` 등)는 **커밋된다.** 처음 실행 결과를 보고 assertion을 반대로 잘못 짰다가(롤백을 기대) 실패해서 알아챘다.
- `readOnly=true`이면 그 트랜잭션 안에서 쓰기 쿼리를 실행하면 예외가 날 거라 예상했다 — **틀렸다.** `DataSourceTransactionManager`는 기본적으로 `enforceReadOnly`가 꺼져 있어서, `readOnly=true`는 그냥 힌트일 뿐 실제 쓰기를 막지 않는다. 힌트라는 레퍼런스 문서의 표현을 읽었을 때는 크게 와닿지 않았는데, 직접 쓰기를 실행해서 성공하는 걸 보고서야 체감했다.
- self-invocation과 private 메서드 무시는 11·12주차에서 이미 다뤘으니 이번엔 "예상"이 아니라 "재확인"에 가까웠다 — 실제로 정확히 같은 이유(프록시가 인스턴스와 별개 객체, private 메서드는 오버라이드 불가)로 재현됐다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/transaction-propagation-playground`](../../experiments/transaction-propagation-playground)
```java
@Transactional
public void noRollbackOnCheckedExceptionByDefault(int accountId, int delta) throws Exception {
    adjustBalance(accountId, delta);
    throw new java.io.IOException("checked failure, but @Transactional's default rollback rule ignores it");
}

@Transactional(rollbackFor = Exception.class)
public void rollbackOnCheckedExceptionWithRollbackFor(int accountId, int delta) throws Exception {
    adjustBalance(accountId, delta);
    throw new java.io.IOException("checked failure, but rollbackFor now covers it");
}
```

**축소 구현** — [`mini-spring/mini-transaction`](../../mini-spring/mini-transaction)
```java
public final class MiniTransactionInterceptor implements MethodInterceptor {
    public Object invoke(MethodInvocation invocation) throws Throwable {
        MiniTransactionStatus status = transactionManager.begin();
        try {
            Object result = invocation.proceed();
            transactionManager.commit(status);
            return result;
        } catch (Throwable ex) {
            transactionManager.rollback(status);   // mini는 예외 종류를 구분하지 않는다 (10번)
            throw ex;
        }
    }
}
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `TransactionInterceptor` | `MethodInterceptor` 구현 — `invoke()`는 `invokeWithinTransaction()`에 위임할 뿐, 로직 대부분은 `TransactionAspectSupport`에 있다 |
| `TransactionAspectSupport` | `invokeWithinTransaction`: 트랜잭션 시작 → `invocation.proceed()` → 예외면 `completeTransactionAfterThrowing`, 정상이면 커밋 |
| `TransactionAttribute` (`DefaultTransactionAttribute`/`RuleBasedTransactionAttribute`) | `rollbackOn(Throwable)` — "이 예외가 롤백을 유발하는가"를 결정하는 단 하나의 메서드 |
| `PlatformTransactionManager`/`AbstractPlatformTransactionManager` | `getTransaction`/`commit`/`rollback` — 실제 트랜잭션 생명주기 |
| `DataSourceTransactionManager` | JDBC `Connection` 기반 구현 — `autoCommit=false` 전환, `readOnly` 힌트 처리(`enforceReadOnly`가 켜져 있을 때만 `Connection#setReadOnly` 호출) |
| `TransactionStatus` | 현재 트랜잭션의 상태(신규 여부, rollback-only 여부 등) — mini의 `MiniTransactionStatus`가 이 역할의 축소판 |
| (mini) `MiniTransactionInterceptor` | 실제 `TransactionInterceptor`와 이름/역할 대응, 단 예외 종류 구분 없이 항상 롤백(10번) |
| (mini) `JdbcMiniTransactionManager` | `PlatformTransactionManager`/`DataSourceTransactionManager`에 대응 — `ThreadLocal<Connection>`으로 구현 |

## 6. 호출 흐름

```text
프록시 호출 (11·12주차에서 다룬 것과 동일한 프록시 - AbstractAdvisorAutoProxyCreator가
  @Transactional을 인식하는 TransactionAttributeSourcePointcut으로 Advisor를 자동 등록한다)
  → TransactionInterceptor#invoke(invocation)
      → invokeWithinTransaction(method, targetClass, invocation::proceed)
          → TransactionAttributeSource에서 이 메서드의 TransactionAttribute 조회
          → PlatformTransactionManager 선택 (determineTransactionManager)
          → createTransactionIfNecessary → tm.getTransaction(txAttr) → TransactionStatus 확보
          → try { invocation.proceedWithInvocation() }  ← 대상 메서드 실제 실행
          → 정상 반환:
              tm.commit(status)
          → 예외 발생:
              completeTransactionAfterThrowing(txInfo, ex)
                → txAttr.rollbackOn(ex)?
                    → 예(RuntimeException/Error, 또는 rollbackFor 매칭): tm.rollback(status)
                    → 아니오(checked 예외, 기본 규칙): tm.commit(status)  ← 커밋된다!
              throw ex   (원래 예외는 그대로 호출자에게 다시 던져진다 - 트랜잭션 처리 여부와 무관)
```

기본 규칙과 `rollbackFor`의 분기, 그리고 self-invocation/private 메서드가 이 흐름에 아예 진입하지 못하는 지점을 함께 그린 다이어그램: [`diagrams/transaction-interceptor-flow.md`](diagrams/transaction-interceptor-flow.md)

## 7. 브레이크포인트

이번 주제는 실행 결과(8번)와 소스 확인(6·9번)으로 검증했고 `tools/jdi-tracer`로 직접 추적하지는 않았다.

```text
org.springframework.transaction.interceptor.TransactionInterceptor#invoke
org.springframework.transaction.interceptor.TransactionAspectSupport#invokeWithinTransaction
org.springframework.transaction.interceptor.TransactionAspectSupport#completeTransactionAfterThrowing
org.springframework.transaction.interceptor.DefaultTransactionAttribute#rollbackOn
org.springframework.jdbc.datasource.DataSourceTransactionManager#doBegin
```

## 8. 런타임 관찰

[`AccountServiceTransactionTest`](../../experiments/transaction-propagation-playground/src/test/java/lab/experiments/tx/AccountServiceTransactionTest.java) (9개, H2 임베디드 DB + `DataSourceTransactionManager`):

| 실험 | 결과 |
| --- | --- |
| 정상 반환 | 커밋, 잔액 변경 유지 |
| `RuntimeException` | 롤백, 잔액 원상복구 |
| checked `Exception`(`IOException`), `rollbackFor` 없음 | **커밋된다** — 잔액 변경이 그대로 남는다 |
| checked `Exception`, `@Transactional(rollbackFor = Exception.class)` | 롤백, 잔액 원상복구 |
| `readOnly=true`에서 `TransactionSynchronizationManager.isCurrentTransactionReadOnly()` | `true` — 힌트 자체는 정확히 전달됨 |
| `readOnly=true`에서 실제 쓰기 쿼리 실행 | **예외 없이 성공** — `DataSourceTransactionManager`의 `enforceReadOnly` 기본값이 꺼져 있어서 |
| self-invocation(`this.rollbackOnRuntimeException(...)` 내부 호출) | 트랜잭션이 아예 시작되지 않음 — 예외가 나도 JDBC autocommit으로 그대로 반영되어 롤백 안 됨 |
| `private` 메서드에 `@Transactional` | 프록시가 감지 못 함 — `TransactionSynchronizationManager.isActualTransactionActive()`가 내부에서 `false` |
| 메서드 안에서 예외를 던지고 즉시 `catch`로 삼킴 | 예외가 `TransactionInterceptor`까지 전혀 도달하지 않으므로 커밋됨 |

[`MiniTransactionManagerTest`](../../mini-spring/mini-transaction/src/test/java/lab/minispring/transaction/MiniTransactionManagerTest.java) (7개, H2 + `ThreadLocal<Connection>`):

| 실험 | 결과 |
| --- | --- |
| 스레드에 트랜잭션이 없을 때 `begin()` | 새 `Connection`, `autoCommit=false`, `isNewTransaction()=true` |
| 같은 스레드에서 두 번째 `begin()` | 첫 번째와 **동일한** `Connection` 객체, `isNewTransaction()=false`(참여) |
| 참여자(non-owner)가 `commit()` | 아무 일도 안 함 — 스레드에 트랜잭션이 여전히 남아 있음(세 번째 `begin()`으로 확인) |
| 서로 다른 스레드에서 각각 `begin()` | 완전히 다른 `Connection` 객체 — `ThreadLocal` 격리가 실제로 작동 |
| `MiniProxyFactory` + `MiniTransactionInterceptor`로 감싼 성공 케이스 | 커밋, DB에 반영 |
| 같은 구성으로 감싼 실패 케이스(잔액 부족) | 롤백, DB 변경 없음 |
| Facade(프록시 경유 내부 호출)로 두 번 이체, 두 번째가 실패 | 참여자의 `rollback()`은 아무것도 안 하지만, **같은 물리적 커넥션을 공유**하기 때문에 주인의 `rollback()`이 첫 번째 이체까지 함께 되돌림 |

## 9. 공식 테스트 분석

`spring-framework` v6.2.19 소스의 `RuleBasedTransactionAttributeTests`(`spring-tx`)로 확인했다.

- **`defaultRule()`**: `new RuleBasedTransactionAttribute().rollbackOn(...)`을 `RuntimeException`/커스텀 `RuntimeException` 서브클래스/`Exception`/`IOException`에 각각 호출해서, 앞의 둘은 `true`(롤백), 뒤의 둘은 `false`(커밋)임을 확인한다 — 우리 `checkedExceptionDoesNotRollBackByDefault` 테스트가 통합 테스트 레벨에서 확인한 것과 정확히 같은 규칙을, 이 공식 테스트는 `TransactionAttribute` 단위에서 직접 검증한다.
- **`ruleForRollbackOnChecked()`**: `RollbackRuleAttribute(IOException.class)`를 추가하면 "기본 동작이 오버라이드된다"는 것을 명시적으로 검증한다(`// Check that default behavior is overridden` 주석까지 있다) — 우리 `rollbackForExtendsTheDefaultRuleToCheckedExceptions` 테스트와 같은 메커니즘이다. `@Transactional(rollbackFor = ...)`는 결국 이 `RollbackRuleAttribute` 목록을 채우는 애노테이션 문법일 뿐이라는 것도 확인했다.
- **`readOnly`의 힌트 전용 기본 동작(H2에서 실제로 쓰기가 통과하는 것)이나 self-invocation/private 메서드를 직접 검증하는 공식 단위 테스트는 찾지 못했다** — 정직하게 밝혀 둔다. `readOnly`는 소스(5번에서 인용한 `isEnforceReadOnly` 가드)로, self-invocation/private 메서드는 11·12주차와 같은 구조적 근거로 확인했다.

## 10. 축소 구현 (구현한 것 / 생략한 것)

`mini-spring/mini-transaction`(project 22) — 1~2단계만 구현했다. `mini-container`(빈 생성/`BeanPostProcessor`)가 아니라 `mini-aop`(11주차, 인터셉터 체인)에 의존하게 했다 — 트랜잭션 어드바이스도 결국 `MethodInterceptor` 하나일 뿐이라는 것을 구조로 드러내기 위해서다.

**구현한 것**
- `JdbcMiniTransactionManager`: `ThreadLocal<Connection>`으로 스레드당 트랜잭션 하나를 추적, `begin()`이 새로 시작(owner)했는지 기존 것에 참여(participant)했는지를 `MiniTransactionStatus.isNewTransaction()`으로 구분
- `MiniTransactionInterceptor`: 실제 `TransactionInterceptor`와 같은 try/proceed/commit, catch/rollback 뼈대
- `getCurrentConnection()`: 실제 Spring의 `DataSourceUtils.getConnection(dataSource)`에 대응 — 대상 코드가 새 커넥션을 얻지 않고 스레드에 바인딩된 커넥션을 가져오게 함

**생략한 것 (의도적)**
- **`rollbackOn(Throwable)` 상당의 예외 종류 판별이 없다** — mini는 `catch (Throwable ex)`에서 예외 종류를 가리지 않고 무조건 롤백한다. 이번 주의 핵심 발견(체크 예외는 기본적으로 커밋된다)을 mini에는 반영하지 않았다 — "왜 그런 규칙이 필요한가"를 이해하는 것과 "그 규칙을 재구현하는 것"은 별개라고 판단했고, 다음 세션(14주차, 전파 속성)에서 `rollback-only` 규칙을 다룰 때 함께 확장할 여지로 남겨 뒀다.
- **`readOnly`/`timeout` 속성 자체가 없다** — `MiniTransactionStatus`/`begin()`이 이런 부가 속성을 받지 않는다.
- **rollback-only 전파가 없다** — 8번의 마지막 테스트에서 보듯, 참여자의 실패가 주인에게 명시적으로 전파되지 않는다. 지금 통과하는 이유는 "같은 물리적 커넥션을 공유해서 우연히 안전한 것"이지, 의도적으로 설계된 전파 규칙이 아니다 — 이 구분 자체가 14주차에서 `REQUIRED`를 제대로 구현해야 하는 이유다.

## 11. Spring 설계 의도

- **왜 checked 예외는 기본적으로 롤백하지 않는가**: 레퍼런스 매뉴얼이 명시하듯 EJB의 관례를 이어받은 것인데, 그 관례의 배경은 "checked 예외는 종종 **비즈니스적으로 정상적인 대안 흐름**(재고 부족, 잔액 부족처럼 호출자가 처리하고 넘어갈 수 있는 상황)을 표현하는 데 쓰이고, unchecked 예외(`RuntimeException`)는 **예상하지 못한 시스템 오류**를 표현하는 데 쓰인다"는 구분이다. "비즈니스적으로 정상적인 대안 흐름"이라면 지금까지의 작업을 굳이 롤백할 필요가 없을 수 있다 — 그래서 Spring은 "예외가 났다"가 아니라 "이 예외의 **종류**가 무엇인가"를 롤백 판단 기준으로 삼는다. 물론 이 구분이 항상 실제 코드베이스의 예외 설계와 맞아떨어지는 건 아니라서, `rollbackFor`로 언제든 뒤집을 수 있게 열어 뒀다.
- **왜 `readOnly`는 강제하지 않는 힌트로 남아 있는가**: `DataSourceTransactionManager`의 주석(`isEnforceReadOnly` 근처)은 "최근 드라이버들은 `Connection.setReadOnly(true)`를 최적화 힌트로 활용하지만, 일부 드라이버/DB는 오히려 예상 밖의 방식으로 반응할 수 있다"는 취지를 밝힌다 — 모든 JDBC 드라이버가 read-only 커넥션에서 쓰기를 안전하게 거부하거나 최적화한다는 보장이 없으므로, 기본값을 "강제로 막는다"가 아니라 "필요하면 명시적으로 켜라"로 잡은 것이다. 실무에서는 이 힌트가 실제로는 (Hibernate의 flush 모드 최적화처럼) ORM 레벨에서 더 적극적으로 활용된다.
- **왜 self-invocation/private 메서드 무시는 `@Transactional`에서도 "버그"가 아니라 "구조적 한계"인가**: 11·12주차에서 확인했듯 이는 `TransactionInterceptor`만의 특수한 제약이 아니라, 프록시 기반 AOP 전체에 적용되는 근본 제약이다. `@Transactional`이 실무에서 유독 이 문제로 자주 사고가 나는 이유는, 트랜잭션 경계가 "이 메서드가 끝나는 시점에 커밋된다"는 **암묵적**이고 **눈에 안 보이는** 계약이라서, 실수로 self-invocation이나 private 메서드에 걸어 두면 컴파일도 되고 대부분 정상 동작하는 것처럼 "보이다가" 데이터 정합성이 깨지는 시점에야 발견되기 때문이다 — 프록시 자체의 한계는 11·12주차와 동일하지만, 그 한계가 만드는 실무적 파장은 `@Transactional`에서 훨씬 크다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: checked 예외는 `@Transactional`의 기본 규칙에서 **롤백되지 않는다** — "예외 = 롤백"이라는 직관과 어긋나는, 그리고 실무에서 가장 자주 사고로 이어진다고 알려진 함정을 직접 실행해서 확인했다.
- 예상 밖이었던 것: `readOnly=true`가 쓰기를 실제로 막지 않는다는 것 — 레퍼런스 문서의 "힌트"라는 표현을 읽었을 때보다 직접 실행해서 쓰기가 성공하는 걸 봤을 때 훨씬 더 명확하게 이해됐다.
- 예상대로였던 것(재확인): self-invocation과 private 메서드의 `@Transactional`은 11·12주차와 완전히 같은 이유로 조용히 무시된다 — 프록시 기반 AOP의 구조적 한계가 어드바이스 종류를 가리지 않는다는 것을 다시 확인했다.
- Mini 구현이 보여준 것: `rollbackOn(Throwable)`처럼 "예외 종류로 롤백 여부를 가른다"는 규칙은 트랜잭션 관리의 핵심 기능인데도, 인터셉터 뼈대(begin/proceed/commit/rollback) 자체와는 독립적으로 나중에 얹을 수 있는 부분이라 mini에서는 의도적으로 생략했다 — "구조를 이해하는 것"과 "정책을 재구현하는 것"이 분리될 수 있다는 걸 보여준다.
- 새로 열린 질문: mini의 마지막 테스트(참여자 실패가 우연히 안전한 것)가 정확히 짚어 준다 — "왜 우연이 아니라 의도된 전파 규칙이 필요한가"가 14주차(트랜잭션 전파와 자원 바인딩)의 출발점이다.

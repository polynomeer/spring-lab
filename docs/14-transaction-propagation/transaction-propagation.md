# 트랜잭션 전파와 자원 바인딩 — 참여, 독립, 그리고 우연이 아닌 안전

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md) 14주차, [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md) 프로젝트 21(Transaction Propagation Playground)·프로젝트 22(Mini Transaction Manager, 3~6단계)에 대응하는 분석 문서다.

## 1. 이번 질문

- JDBC `Connection`은 어떻게 현재 스레드에 묶이는가? 같은 트랜잭션 안에서 여러 번 DB 접근을 해도 항상 같은 `Connection`이 쓰이는 이유는?
- `REQUIRED`와 `REQUIRES_NEW`는 내부적으로 무엇이 다른가? "suspend"와 "resume"은 정확히 무엇을 하는 동작인가?
- `NESTED`는 `REQUIRED`와 무엇이 다른가 — 특히 내부가 실패했을 때 바깥 트랜잭션에 미치는 영향이?
- 13주차 mini-transaction이 "참여자의 실패를 owner에게 전혀 알리지 않는데도 우연히 안전했던" 그 지점 — 실제로 rollback-only 전파를 구현하면 무엇이 달라지는가?

## 2. 공식 문서 요약

- Spring 레퍼런스 매뉴얼(Transaction Management, "Transaction Propagation")은 7가지 전파 속성을 정의하지만, 이번 주 실험은 그중 `REQUIRED`(기본값, 참여), `REQUIRES_NEW`(항상 새 트랜잭션, 기존 것은 잠시 미뤄둠), `NESTED`(savepoint 기반 하위 트랜잭션), `NOT_SUPPORTED`(비트랜잭션으로 실행, 기존 것은 잠시 미뤄둠)에 집중한다.
- `REQUIRES_NEW`/`NOT_SUPPORTED`가 "기존 트랜잭션을 suspend한다"는 표현을 명시적으로 쓴다 — 완전히 종료시키는 게 아니라 "잠시 스레드에서 떼어냈다가 나중에 그대로 이어 붙인다"는 의미다.
- `NESTED`는 JDBC savepoint를 지원하는 드라이버에서만 동작하며, "내부 트랜잭션의 실패가 외부 트랜잭션까지 반드시 롤백시키지는 않는다"고 설명한다 — `REQUIRED`(참여)의 rollback-only 전파와 대비되는 지점이다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `REQUIRES_NEW`도 결국 같은 스레드에서 실행되니 내부적으로는 같은 `Connection`을 재사용하고 커밋 시점만 다를 거라 예상했다 — **틀렸다.** `REQUIRES_NEW`는 정말로 **다른 물리적 `Connection`**을 새로 얻는다. 기존 연결은 닫히는 게 아니라 스레드에서 떼어졌다가(suspend) 나중에 그대로 복원된다(resume).
- `NESTED`도 `REQUIRES_NEW`처럼 별도의 `Connection`을 쓸 거라 예상했다 — **틀렸다.** `NESTED`는 같은 `Connection`, 같은 물리적 트랜잭션 안에서 savepoint로만 구현된다 — DB 커넥션 자체는 그대로다.
- `NESTED`가 실패하면 `REQUIRED`처럼 바깥 트랜잭션도 rollback-only로 오염될 거라 예상했다 — **틀렸다.** savepoint까지만 롤백되고, 바깥 트랜잭션은 멀쩡히 커밋할 수 있다. 실행해 보기 전까지 `NESTED`와 `REQUIRED`가 "실패 시 파급 범위"에서 이렇게 다르다는 걸 체감하지 못했다.
- mini-transaction의 rollback-only 전파를 구현하면서, `commit()`을 `proceed()`를 감싸는 `try` 안에 그냥 넣어도 될 거라 예상했다 — **틀렸다.** `commit()` 자체가 예외를 던질 수 있게 되자, 같은 `catch`가 그걸 "실행 실패"로 오인해서 이미 끝난 트랜잭션을 또 롤백하려다 터졌다(8·10번에서 자세히).

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/transaction-propagation-playground`](../../experiments/transaction-propagation-playground)
```java
@Transactional  // REQUIRED (기본값)
public void placeOrderInnerRequiresNew(boolean innerFails) {
    ledgerRepository.record("order");
    paymentService.payRequiresNew(innerFails);   // 독립된 새 트랜잭션
    ledgerRepository.record("audit");
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void payRequiresNew(boolean fail) {
    ledgerRepository.record("payment");
    if (fail) throw new PaymentFailedException("...");
}
```

**축소 구현** — [`mini-spring/mini-transaction`](../../mini-spring/mini-transaction)
```java
public MiniTransactionStatus begin(MiniPropagation propagation) {
    MiniConnectionHolder existing = holderThreadLocal.get();
    if (propagation == MiniPropagation.REQUIRED && existing != null) {
        return new MiniTransactionStatus(existing, false, null);   // 참여
    }
    MiniConnectionHolder suspended = (propagation == MiniPropagation.REQUIRES_NEW) ? existing : null;
    if (suspended != null) holderThreadLocal.remove();             // suspend
    Connection connection = dataSource.getConnection();
    connection.setAutoCommit(false);
    MiniConnectionHolder holder = new MiniConnectionHolder(connection);
    holderThreadLocal.set(holder);
    return new MiniTransactionStatus(holder, true, suspended);     // owner, 나중에 resume할 대상 보유
}
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `TransactionSynchronizationManager` | `Map<Object, Object> resources`라는 스레드 바인딩 자원 레지스트리 - 키는 `DataSource`, 값은 `ConnectionHolder` |
| `DataSourceUtils` | `getConnection(dataSource)`/`releaseConnection(...)` - `TransactionSynchronizationManager`의 자원 맵을 참조 카운트 방식으로 감싼 공개 API |
| `ConnectionHolder` | 커넥션 자체 + `transactionActive`/`rollbackOnly` 등 부가 상태 (mini의 `MiniConnectionHolder`가 이 역할) |
| `AbstractPlatformTransactionManager#suspend`/`resume` | 기존 트랜잭션(과 그 Synchronization 콜백들, 격리 수준, readOnly 여부까지)을 통째로 떼어냈다가 복원 |
| `DataSourceTransactionManager#doSuspend`/`doResume` | 실제로는 `TransactionSynchronizationManager.unbindResource`/`bindResource` 호출일 뿐 |
| `useSavepointForNestedTransaction()` | `NESTED`를 savepoint로 구현할지 결정 (`DataSourceTransactionManager`는 기본 지원) |
| (mini) `JdbcMiniTransactionManager#holderThreadLocal` | `TransactionSynchronizationManager`의 자원 맵을 극단적으로 단순화한 버전 - DataSource 하나만 지원 |
| (mini) `MiniTransactionStatus#suspendedHolder` | `SuspendedResourcesHolder`에 대응 - REQUIRES_NEW가 끝나면 이걸로 resume |

## 6. 호출 흐름

```text
[REQUIRED, 기존 트랜잭션 있음 - 참여]
begin(REQUIRED) → holderThreadLocal에 이미 홀더가 있다 → 그 홀더 그대로 재사용
  → MiniTransactionStatus(newTransaction=false)
  → commit()/rollback() 모두 owner에게 위임 (commit: no-op, rollback: rollback-only 표시만)

[REQUIRES_NEW - 항상 새 트랜잭션]
begin(REQUIRES_NEW) → 기존 홀더(있다면) 저장해 두고 holderThreadLocal에서 제거(suspend)
  → 새 Connection 획득, autoCommit(false), holderThreadLocal에 새 홀더 바인딩
  → MiniTransactionStatus(newTransaction=true, suspendedHolder=기존 홀더)
  → 대상 코드 실행 - getCurrentConnection()은 이 "새" 홀더의 커넥션을 돌려준다
  → commit()/rollback() (owner이므로 실제 수행) → release()
      → holderThreadLocal.remove() (새 홀더 해제) → 새 Connection.close()
      → suspendedHolder가 있으면 holderThreadLocal에 다시 바인딩(resume)

[NESTED - 같은 Connection, savepoint]
(mini는 구현하지 않음 - 실제 Spring 기준)
begin() → 기존 트랜잭션에 savepoint 하나 생성 (Connection은 그대로)
  → 대상 코드 실행
  → 실패 시: savepoint까지만 rollback (status.hasSavepoint() 분기, 바깥 트랜잭션은 rollback-only로
    표시되지 않음)
  → 성공 시: savepoint 해제(release), 바깥 트랜잭션이 나중에 커밋할 때 자연스럽게 함께 반영
```

REQUIRED/REQUIRES_NEW/NESTED 세 경로를 나란히 그린 시퀀스 다이어그램: [`diagrams/propagation-paths.md`](diagrams/propagation-paths.md)

## 7. 브레이크포인트

이번 주제는 실행 결과(8번)와 소스 확인(6·9번)으로 검증했고 `tools/jdi-tracer`로 직접 추적하지는 않았다.

```text
org.springframework.transaction.support.AbstractPlatformTransactionManager#getTransaction
org.springframework.transaction.support.AbstractPlatformTransactionManager#suspend
org.springframework.transaction.support.AbstractPlatformTransactionManager#resume
org.springframework.jdbc.datasource.DataSourceTransactionManager#doSuspend
org.springframework.jdbc.datasource.DataSourceTransactionManager#doResume
org.springframework.transaction.support.TransactionSynchronizationManager#bindResource
```

## 8. 런타임 관찰

[`TransactionPropagationTest`](../../experiments/transaction-propagation-playground/src/test/java/lab/experiments/tx/TransactionPropagationTest.java) (10개, H2 + `DataSourceTransactionManager`):

| 실험 | 결과 |
| --- | --- |
| `REQUIRED` 안에서 `REQUIRED` 호출 | **같은** `Connection` identity, `isActualTransactionActive()=true` |
| `REQUIRED` 안에서 `REQUIRES_NEW` 호출 | **다른** `Connection` identity |
| `REQUIRED` 안에서 `NESTED` 호출 | `REQUIRES_NEW`와 달리 `REQUIRED`와 **같은** `Connection` identity |
| `REQUIRED` 안에서 `NOT_SUPPORTED` 호출 | `isActualTransactionActive()=false` |
| `REQUIRED` 참여자 실패, 외부에서 안 잡음 | 전체 롤백(`order`/`payment` 모두 사라짐) |
| `REQUIRED` 참여자 실패, 외부에서 catch | `UnexpectedRollbackException`이 **호출자에게** 던져짐, 전체 롤백(`order`/`payment`/`audit` 모두 사라짐) |
| `REQUIRES_NEW` 실패, 외부에서 catch | 예외 없음(정상 완료), `payment`만 사라지고 `order`/`audit`는 커밋됨 |
| `NESTED` 실패, 외부에서 catch | 예외 없음, `payment`만 사라지고 `order`/`audit`는 커밋됨(REQUIRES_NEW와 최종 결과는 같지만 6번처럼 메커니즘은 다름) |
| `REQUIRES_NEW` 성공 후 외부가 나중에 실패 | `order`는 롤백, 이미 독립적으로 커밋된 `payment`는 그대로 남음 |
| `NOT_SUPPORTED`에서 결제 자체가 실패 | `order`는 롤백되지만, 트랜잭션이 아예 없었던 `payment` 기록은 **그대로 남음** |

[`MiniTransactionManagerTest`](../../mini-spring/mini-transaction/src/test/java/lab/minispring/transaction/MiniTransactionManagerTest.java) (12개, 3~6단계 관련 5개 추가):

| 실험 | 결과 |
| --- | --- |
| `REQUIRES_NEW`로 `begin()` | 기존 트랜잭션과 **다른** `Connection`, `isNewTransaction()=true` |
| `REQUIRES_NEW` 종료 후 다시 `REQUIRED`로 `begin()` | 원래 트랜잭션의 `Connection`으로 정확히 복원(resume) |
| 참여자 실패 후 owner가 그 예외를 **삼키지 않고** 전파 | 원래 예외(`IllegalStateException`) 그대로 전파, 데이터는 롤백(우연히 안전 - 13주차와 동일) |
| 참여자 실패 후 owner가 그 예외를 **삼킴** | `MiniUnexpectedRollbackException` - rollback-only 전파(5단계)가 없었다면 조용히 커밋됐을 상황 |
| `REQUIRES_NEW`로 커밋된 변경 + 이후 외부 트랜잭션 롤백 | `REQUIRES_NEW` 쪽 변경은 그대로 남음 |
| Synchronization 콜백(commit/rollback) | 등록한 순서대로 `beforeCommit`→`afterCommit`, 또는 `afterRollback`이 정확히 호출됨 |

**직접 겪은 버그** (5번에서 예상했던 것): `MiniTransactionInterceptor`가 `commit()`을 `proceed()`를 감싸는 `try` **안에** 두고 있었다. rollback-only 전파를 구현해서 `commit()`이 `MiniUnexpectedRollbackException`을 던질 수 있게 되자, 그 예외가 같은 `catch (Throwable ex)`에 잡혀서 **이미 커넥션이 닫힌 트랜잭션에 또 `rollback()`을 시도**하다가 "connection is closed" 오류가 났다. 실제 Spring의 `TransactionAspectSupport.invokeWithinTransaction()` 소스를 다시 확인해 보니, `commitTransactionAfterReturning(txInfo)` 호출이 `proceed()`를 감싸는 `try`/`catch`/`finally` **바깥**에 있었다 — 그 구조를 그대로 따라서 `commit()` 호출을 `try` 밖으로 옮겨 해결했다.

## 9. 공식 테스트 분석

`spring-framework` v6.2.19 소스의 `DataSourceTransactionManagerTests`(`spring-jdbc`)로 확인했다.

- **`propagationRequiresNewWithExistingTransaction()`**: 바깥 `TransactionTemplate`(`REQUIRES_NEW`) 안에 또 다른 `REQUIRES_NEW`를 중첩시켜, `status2.isNewTransaction()`도 `true`임을 확인하고, 안쪽에서 `setRollbackOnly()`를 호출한 뒤 최종적으로 `verify(con).rollback()`(안쪽), `verify(con).commit()`(바깥), `verify(con, times(2)).close()`(둘 다 별개의 물리적 커넥션/트랜잭션이라 각자 닫힘)를 검증한다 — 우리 `requiresNewSuspendsAndUsesADifferentConnection`과 `requiresNewCommitsIndependentlyEvenWhenTheOuterTransactionLaterFails`가 확인한 것과 정확히 같은 메커니즘을, Mock 기반으로 더 정밀하게 검증한다.
- **`existingTransactionWithPropagationNestedAndRollback()`**: `NESTED`로 시작한 트랜잭션이 롤백되면 savepoint까지만 롤백되고 바깥 트랜잭션 자체는 영향받지 않는다는 것을 확인한다 — 우리 `nestedInnerFailureCaughtByOuterDoesNotAffectTheOuterTransaction`과 같은 결론이다.
- **REQUIRED 참여자 실패 시 `UnexpectedRollbackException`이 정확히 어느 호출자에게 던져지는지, mini의 rollback-only 전파 버그(commit()을 try 안에 두면 안 되는 이유)를 직접 검증하는 공식 테스트는 찾지 못했다** — 정직하게 밝혀 둔다. 전자는 `AbstractPlatformTransactionManager`의 `commit()`/`processCommit()` 소스(13주차 문서 9번에서 이미 인용)로, 후자는 `TransactionAspectSupport.invokeWithinTransaction()`의 try/catch/finally 구조를 직접 재확인해서 검증했다.

## 10. 축소 구현 (구현한 것 / 생략한 것)

`mini-spring/mini-transaction`(project 22) — 이번 주에 3~6단계를 마무리했다.

**구현한 것**
- `begin(MiniPropagation)`: `REQUIRED`(참여 또는 신규), `REQUIRES_NEW`(항상 신규 + suspend/resume)
- `MiniConnectionHolder`: 커넥션 + `rollbackOnly` 플래그 + `Synchronization` 콜백 목록 - 실제 `ConnectionHolder`의 축소판
- rollback-only 전파: 참여자의 `rollback()`이 이제 실제로 `holder.markRollbackOnly()`를 호출하고, owner의 `commit()`이 이를 확인해 필요하면 실제 롤백 + `MiniUnexpectedRollbackException`
- `registerSynchronization()`: 스레드에 바인딩된 현재 트랜잭션에 콜백을 등록(실제 `TransactionSynchronizationManager.registerSynchronization()`에 대응, 단 별도 클래스로 분리하지 않고 트랜잭션 매니저에 포함)

**생략한 것 (의도적)**
- **`NESTED`(savepoint)가 없다** — mini가 다루는 것은 REQUIRED/REQUIRES_NEW까지다(카탈로그 발전 단계 원문 그대로). savepoint는 JDBC API 자체(`Connection#setSavepoint`)를 다루는 별도의 학습 주제라 이번 범위 밖으로 뒀다.
- **여러 `DataSource`를 지원하지 않는다** — 실제 `TransactionSynchronizationManager`는 `Map<Object, Object> resources`로 여러 자원(여러 `DataSource`, JMS 등)을 동시에 스레드에 바인딩할 수 있지만, mini의 `holderThreadLocal`은 `JdbcMiniTransactionManager` 인스턴스 하나당 `DataSource` 하나만 다룬다.
- **격리 수준(`isolationLevel`)/트랜잭션 이름은 suspend/resume 대상에 없다** — 실제 `SuspendedResourcesHolder`는 이런 부가 정보까지 함께 들고 있다가 복원하지만, mini는 커넥션과 rollback-only 관련 상태만 다룬다.

## 11. Spring 설계 의도

- **왜 `TransactionSynchronizationManager`는 `PlatformTransactionManager`와 분리된 별도의 정적 클래스인가**: "현재 스레드에 어떤 자원이 묶여 있는가"는 트랜잭션 매니저 하나의 관심사가 아니다 — `JdbcTemplate`, `HibernateTemplate` 등 트랜잭션과 무관해 보이는 코드도 "지금 트랜잭션 안에 있다면 그 커넥션을 재사용해야 한다"는 사실을 알아야 한다. 이 지식을 트랜잭션 매니저 구현체 하나에 가두지 않고 스레드 전역의 정적 레지스트리로 분리해 둔 덕에, 트랜잭션 관리와 무관한 코드도 `DataSourceUtils.getConnection()` 하나만 호출하면 "지금 트랜잭션이 있으면 참여하고, 없으면 새로 연다"는 동작을 얻는다.
- **왜 `REQUIRES_NEW`는 기존 트랜잭션을 "종료"가 아니라 "suspend"시키는가**: `REQUIRES_NEW`가 끝난 뒤에도 바깥 트랜잭션은 자신이 이미 해 온 작업(같은 커넥션 위의 이전 변경들)을 계속 이어가야 한다. 만약 suspend 없이 그냥 새 트랜잭션을 얹기만 한다면, 바깥 트랜잭션의 상태(현재 트랜잭션 이름, 격리 수준, readOnly 여부, 이미 등록된 Synchronization 콜백들)가 안쪽 트랜잭션의 그것과 섞여 버릴 위험이 있다 — suspend/resume은 "완전히 다른 트랜잭션 컨텍스트로 잠깐 갈아탔다가 원래 컨텍스트로 정확히 돌아온다"는 것을 보장하기 위한 장치다.
- **왜 `NESTED`는 별도 커넥션 없이 savepoint로 구현되는가**: `NESTED`가 표현하려는 의미는 "바깥 트랜잭션의 일부지만, 이 부분만 따로 취소할 수 있다"는 것이다. 이는 물리적으로 트랜잭션을 통째로 분리하는 것(REQUIRES_NEW)과는 다른 요구사항 — 같은 커넥션, 같은 트랜잭션 경계 안에서 "부분 취소"만 가능하면 되므로, JDBC가 이미 제공하는 savepoint 메커니즘을 그대로 재사용하는 것이 가장 자연스러운 구현이다. 이 선택 덕분에 `NESTED` 실패는 savepoint 이전 상태로만 되돌리고, 바깥 트랜잭션은 그 사실조차 모른 채(rollback-only로 오염되지 않고) 계속 진행할 수 있다.
- **왜 mini의 `commit()`을 `try` 밖으로 옮기는 게 "사소한 리팩터링"이 아니라 "구조적으로 필요한 것"인가**: `commit()`이 실패할 수 있다는 것(rollback-only로 인한 `UnexpectedRollbackException`)을 받아들이면, "무엇이 실행 실패이고 무엇이 커밋 실패인가"를 구분해야 한다 — 실행이 성공했는데 커밋이 실패한 경우, 이미 완료된(닫힌) 트랜잭션에 대해 "실행 실패니까 롤백해야지"라고 다시 개입하면 안 된다. 실제 Spring이 `commitTransactionAfterReturning`을 별도의, 독립된 호출로 분리해 둔 것은 바로 이 두 실패 모드(실행 실패 vs 커밋 실패)를 프레임워크 차원에서 명확히 나누기 위한 설계다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: `NESTED`는 `REQUIRES_NEW`와 결과가 비슷해 보이지만(둘 다 "내부 실패가 외부에 번지지 않음") 메커니즘은 완전히 다르다 — `REQUIRES_NEW`는 별도의 물리적 커넥션/트랜잭션, `NESTED`는 같은 커넥션 위의 savepoint. "무엇이 실패해도 안전한가"라는 결과만 보면 구분이 안 되지만, "왜 안전한가"는 완전히 다른 이야기라는 걸 배웠다.
- 예상 밖이었던 것: `NOT_SUPPORTED`로 실행된 코드는 그 어떤 트랜잭션에도 속하지 않아서, 나중에 무슨 일이 있어도(바깥 트랜잭션 롤백은 물론, 그 코드 자신이 실패해도) 절대 되돌릴 수 없다는 것 — "트랜잭션 없음"이 "위험하지 않음"을 뜻하지 않는다는 걸 직접 확인했다.
- Mini 구현이 보여준 것(가장 값진 발견): rollback-only 전파를 "제대로" 구현하려고 하자, 이전에는 드러나지 않았던 구조적 버그(`commit()`을 `proceed()`의 `catch` 안에 둔 것)가 즉시 실패로 나타났다 — 13주차에 "우연히 안전했다"고 적었던 그 지점이, 실제로 안전 장치를 추가하는 순간 오히려 새로운 실패 모드를 만들어 낸 것이다. 이는 "얕은 유사 구현은 겉보기엔 통과하지만, 실제 정책(rollback-only)을 하나씩 추가할 때마다 숨어 있던 구조적 가정이 검증된다"는 이 문서 전체의 방법론(공식 문서 → 최소 예제 → ... → 축소 구현)이 의도한 바로 그 학습 효과다.
- 6단계(Spring AOP)에 이어 7단계(트랜잭션)도 이걸로 마무리된다. 다음은 8단계, Spring MVC(`DispatcherServlet`)로 넘어간다.

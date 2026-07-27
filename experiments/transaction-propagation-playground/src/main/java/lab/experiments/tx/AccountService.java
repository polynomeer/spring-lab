package lab.experiments.tx;

public interface AccountService {

    void commitSuccessfully(int accountId, int delta);

    void rollbackOnRuntimeException(int accountId, int delta);

    void noRollbackOnCheckedExceptionByDefault(int accountId, int delta) throws Exception;

    void rollbackOnCheckedExceptionWithRollbackFor(int accountId, int delta) throws Exception;

    boolean readOnlyIsMarkedAsReadOnlyHint();

    void readOnlyDoesNotPreventWritesByDefault(int accountId, int delta);

    // 이 메서드 자체는 @Transactional이 아니다 - 내부에서 rollbackOnRuntimeException()을
    // this로 직접 호출해서, 그 호출이 프록시를 거치지 않는다는 것을 보여주는 진입점이다.
    void invokeRollbackMethodThroughSelfInvocation(int accountId, int delta);

    // 내부에서 private 메서드를 호출한다 - private 메서드에 @Transactional을 붙여도
    // 프록시가 오버라이드할 수 없어 애초에 감지되지 않는다는 것을 보여주는 진입점이다.
    // 반환값은 그 private 메서드 실행 중 트랜잭션이 실제로 활성 상태였는지 여부다.
    boolean invokePrivateTransactionalMethod(int accountId, int delta);

    void exceptionCaughtInsideDoesNotTriggerRollback(int accountId, int delta);

    int balanceOf(int accountId);
}

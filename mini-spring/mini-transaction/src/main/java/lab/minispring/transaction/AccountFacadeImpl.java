package lab.minispring.transaction;

public final class AccountFacadeImpl implements AccountFacade {

    // 이미 트랜잭션 프록시로 감싸진 Account를 그대로 들고 있다 - this.transfer()가 아니라
    // 이 프록시를 거쳐 호출해야 2단계(기존 트랜잭션 참여)가 실제로 일어난다. self-invocation과
    // 정반대로, "프록시를 거치는 내부 호출"을 만들기 위한 구조다.
    private final Account account;

    public AccountFacadeImpl(Account account) {
        this.account = account;
    }

    @Override
    public void transferTwice(int accountId, int deltaA, int deltaB) {
        account.transfer(accountId, deltaA);
        account.transfer(accountId, deltaB);
    }

    @Override
    public void transferTwiceSwallowingFailures(int accountId, int deltaA, int deltaB) {
        // 두 이체 중 하나가 실패해도 여기서 삼키므로, 이 메서드 자체는 항상 정상적으로
        // 리턴한다 - owner의 commit()이 rollback-only 표시를 보고 실제로는 롤백할지(5단계)
        // 아니면 그대로 커밋해 버릴지(2단계까지의 버그)가 갈리는 지점이다.
        try {
            account.transfer(accountId, deltaA);
        } catch (RuntimeException ignored) {
        }
        try {
            account.transfer(accountId, deltaB);
        } catch (RuntimeException ignored) {
        }
    }
}

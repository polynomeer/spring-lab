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
}

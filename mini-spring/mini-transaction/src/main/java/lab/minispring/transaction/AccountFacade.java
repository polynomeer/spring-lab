package lab.minispring.transaction;

public interface AccountFacade {

    void transferTwice(int accountId, int deltaA, int deltaB);

    void transferTwiceSwallowingFailures(int accountId, int deltaA, int deltaB);
}

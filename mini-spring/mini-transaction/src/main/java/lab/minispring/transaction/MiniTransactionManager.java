package lab.minispring.transaction;

public interface MiniTransactionManager {

    MiniTransactionStatus begin();

    void commit(MiniTransactionStatus status);

    void rollback(MiniTransactionStatus status);
}

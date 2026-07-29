package lab.minispring.transaction;

public interface MiniTransactionManager {

    MiniTransactionStatus begin(MiniPropagation propagation);

    void commit(MiniTransactionStatus status);

    void rollback(MiniTransactionStatus status);
}

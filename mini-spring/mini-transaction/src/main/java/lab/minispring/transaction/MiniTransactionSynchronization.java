package lab.minispring.transaction;

public interface MiniTransactionSynchronization {

    default void beforeCommit() {
    }

    default void afterCommit() {
    }

    default void afterRollback() {
    }
}

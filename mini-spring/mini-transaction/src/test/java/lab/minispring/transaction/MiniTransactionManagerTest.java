package lab.minispring.transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import lab.minispring.aop.MiniProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MiniTransactionManagerTest {

    private DataSource dataSource;

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        dataSource = ds;

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE accounts (id INT PRIMARY KEY, balance INT NOT NULL)");
            statement.execute("INSERT INTO accounts (id, balance) VALUES (1, 100)");
        }
    }

    private int readBalance(int accountId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement("SELECT balance FROM accounts WHERE id = ?")) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt("balance");
            }
        }
    }

    @Test
    void beginCreatesANewTransactionWhenNoneIsActive() throws SQLException {
        JdbcMiniTransactionManager transactionManager = new JdbcMiniTransactionManager(dataSource);

        MiniTransactionStatus status = transactionManager.begin();

        assertThat(status.isNewTransaction()).isTrue();
        assertThat(status.getConnection().getAutoCommit()).isFalse();

        transactionManager.rollback(status);
    }

    @Test
    void secondBeginOnTheSameThreadJoinsTheExistingTransaction() {
        JdbcMiniTransactionManager transactionManager = new JdbcMiniTransactionManager(dataSource);
        MiniTransactionStatus outer = transactionManager.begin();

        MiniTransactionStatus inner = transactionManager.begin();

        assertThat(inner.isNewTransaction()).isFalse();
        assertThat(inner.getConnection()).isSameAs(outer.getConnection());

        transactionManager.commit(inner);
        transactionManager.commit(outer);
    }

    @Test
    void participantCommitIsNoOpUntilTheOwnerCommits() {
        JdbcMiniTransactionManager transactionManager = new JdbcMiniTransactionManager(dataSource);
        MiniTransactionStatus outer = transactionManager.begin();
        MiniTransactionStatus inner = transactionManager.begin();

        transactionManager.commit(inner);

        // 참여자 commit()은 아무것도 하지 않으므로, 스레드에는 여전히 같은 트랜잭션이 떠 있다.
        MiniTransactionStatus stillParticipating = transactionManager.begin();
        assertThat(stillParticipating.isNewTransaction()).isFalse();
        assertThat(stillParticipating.getConnection()).isSameAs(outer.getConnection());

        transactionManager.commit(stillParticipating);
        transactionManager.commit(outer);
    }

    @Test
    void differentThreadsGetIndependentConnections() throws Exception {
        JdbcMiniTransactionManager transactionManager = new JdbcMiniTransactionManager(dataSource);
        MiniTransactionStatus mainThreadStatus = transactionManager.begin();

        Connection[] otherThreadConnection = new Connection[1];
        Thread other = new Thread(() -> {
            MiniTransactionStatus status = transactionManager.begin();
            otherThreadConnection[0] = status.getConnection();
            transactionManager.rollback(status);
        });
        other.start();
        other.join();

        assertThat(otherThreadConnection[0]).isNotSameAs(mainThreadStatus.getConnection());

        transactionManager.rollback(mainThreadStatus);
    }

    @Test
    void interceptorCommitsASuccessfulTransferThroughTheFullProxy() throws SQLException {
        JdbcMiniTransactionManager transactionManager = new JdbcMiniTransactionManager(dataSource);
        JdbcAccountRepository repository = new JdbcAccountRepository(transactionManager);
        MiniProxyFactory factory = new MiniProxyFactory(repository);
        factory.addInterceptor(new MiniTransactionInterceptor(transactionManager));
        Account account = factory.getProxy();

        account.transfer(1, 10);

        assertThat(readBalance(1)).isEqualTo(110);
    }

    @Test
    void interceptorRollsBackAFailingTransferThroughTheFullProxy() throws SQLException {
        JdbcMiniTransactionManager transactionManager = new JdbcMiniTransactionManager(dataSource);
        JdbcAccountRepository repository = new JdbcAccountRepository(transactionManager);
        MiniProxyFactory factory = new MiniProxyFactory(repository);
        factory.addInterceptor(new MiniTransactionInterceptor(transactionManager));
        Account account = factory.getProxy();

        assertThatThrownBy(() -> account.transfer(1, -1000)).isInstanceOf(IllegalStateException.class);

        assertThat(readBalance(1)).isEqualTo(100);
    }

    @Test
    void ownerRollbackUndoesAParticipantsAlreadyAppliedChangesBecauseTheyShareOneConnection() throws SQLException {
        JdbcMiniTransactionManager transactionManager = new JdbcMiniTransactionManager(dataSource);
        JdbcAccountRepository repository = new JdbcAccountRepository(transactionManager);
        MiniProxyFactory accountFactory = new MiniProxyFactory(repository);
        accountFactory.addInterceptor(new MiniTransactionInterceptor(transactionManager));
        Account account = accountFactory.getProxy();

        AccountFacadeImpl facadeTarget = new AccountFacadeImpl(account);
        MiniProxyFactory facadeFactory = new MiniProxyFactory(facadeTarget);
        facadeFactory.addInterceptor(new MiniTransactionInterceptor(transactionManager));
        AccountFacade facade = facadeFactory.getProxy();

        // 두 번째 이체가 잔액을 마이너스로 만들어 실패한다. 참여자(inner)의 rollback()은
        // 2단계 한계상 아무것도 하지 않지만(JdbcMiniTransactionManager 주석 참고), 첫 번째
        // 이체가 이미 적용된 것도 같은 물리적 커넥션을 공유하기 때문에 주인(outer)의
        // rollback()이 한꺼번에 되돌린다 - rollback-only 전파를 구현하지 않았는데도 커넥션을
        // 공유한 덕에 결과적으로 안전한 것이지, 의도적으로 설계한 전파 규칙 때문이 아니다.
        assertThatThrownBy(() -> facade.transferTwice(1, 10, -1000)).isInstanceOf(IllegalStateException.class);

        assertThat(readBalance(1)).isEqualTo(100);
    }
}

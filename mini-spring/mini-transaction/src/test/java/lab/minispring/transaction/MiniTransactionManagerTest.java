package lab.minispring.transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
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

        MiniTransactionStatus status = transactionManager.begin(MiniPropagation.REQUIRED);

        assertThat(status.isNewTransaction()).isTrue();
        assertThat(status.getConnection().getAutoCommit()).isFalse();

        transactionManager.rollback(status);
    }

    @Test
    void secondRequiredBeginOnTheSameThreadJoinsTheExistingTransaction() {
        JdbcMiniTransactionManager transactionManager = new JdbcMiniTransactionManager(dataSource);
        MiniTransactionStatus outer = transactionManager.begin(MiniPropagation.REQUIRED);

        MiniTransactionStatus inner = transactionManager.begin(MiniPropagation.REQUIRED);

        assertThat(inner.isNewTransaction()).isFalse();
        assertThat(inner.getConnection()).isSameAs(outer.getConnection());

        transactionManager.commit(inner);
        transactionManager.commit(outer);
    }

    @Test
    void participantCommitIsNoOpUntilTheOwnerCommits() {
        JdbcMiniTransactionManager transactionManager = new JdbcMiniTransactionManager(dataSource);
        MiniTransactionStatus outer = transactionManager.begin(MiniPropagation.REQUIRED);
        MiniTransactionStatus inner = transactionManager.begin(MiniPropagation.REQUIRED);

        transactionManager.commit(inner);

        // 참여자 commit()은 아무것도 하지 않으므로, 스레드에는 여전히 같은 트랜잭션이 떠 있다.
        MiniTransactionStatus stillParticipating = transactionManager.begin(MiniPropagation.REQUIRED);
        assertThat(stillParticipating.isNewTransaction()).isFalse();
        assertThat(stillParticipating.getConnection()).isSameAs(outer.getConnection());

        transactionManager.commit(stillParticipating);
        transactionManager.commit(outer);
    }

    @Test
    void differentThreadsGetIndependentConnections() throws Exception {
        JdbcMiniTransactionManager transactionManager = new JdbcMiniTransactionManager(dataSource);
        MiniTransactionStatus mainThreadStatus = transactionManager.begin(MiniPropagation.REQUIRED);

        Connection[] otherThreadConnection = new Connection[1];
        Thread other = new Thread(() -> {
            MiniTransactionStatus status = transactionManager.begin(MiniPropagation.REQUIRED);
            otherThreadConnection[0] = status.getConnection();
            transactionManager.rollback(status);
        });
        other.start();
        other.join();

        assertThat(otherThreadConnection[0]).isNotSameAs(mainThreadStatus.getConnection());

        transactionManager.rollback(mainThreadStatus);
    }

    @Test
    void requiresNewSuspendsTheExistingTransactionAndUsesAFreshConnection() {
        JdbcMiniTransactionManager transactionManager = new JdbcMiniTransactionManager(dataSource);
        MiniTransactionStatus outer = transactionManager.begin(MiniPropagation.REQUIRED);

        MiniTransactionStatus inner = transactionManager.begin(MiniPropagation.REQUIRES_NEW);

        assertThat(inner.isNewTransaction()).isTrue();
        assertThat(inner.getConnection()).isNotSameAs(outer.getConnection());

        transactionManager.commit(inner);

        // REQUIRES_NEW가 끝나면 밀어냈던 outer가 스레드에 되돌아와야(resume) 참여할 수 있다.
        MiniTransactionStatus outerAgain = transactionManager.begin(MiniPropagation.REQUIRED);
        assertThat(outerAgain.isNewTransaction()).isFalse();
        assertThat(outerAgain.getConnection()).isSameAs(outer.getConnection());

        transactionManager.commit(outerAgain);
        transactionManager.commit(outer);
    }

    // 아래부터는 7번 절("남겨 둔 질문")이 미뤄 뒀던 NESTED를 나중에 채운 테스트들이다 -
    // docs/14-transaction-propagation 후기 참고.

    @Test
    void beginNestedWithNoExistingTransactionBehavesLikeANewOwnedTransaction() {
        // NESTED가 참여할 대상이 아예 없으면 REQUIRED처럼 그냥 새 트랜잭션을 시작한다 -
        // savepoint를 찍을 "바깥 트랜잭션" 자체가 없기 때문이다.
        JdbcMiniTransactionManager transactionManager = new JdbcMiniTransactionManager(dataSource);

        MiniTransactionStatus status = transactionManager.begin(MiniPropagation.NESTED);

        assertThat(status.isNewTransaction()).isTrue();

        transactionManager.rollback(status);
    }

    @Test
    void nestedTransactionSharesTheSameConnectionAsTheOuterOneUnlikeRequiresNew() {
        JdbcMiniTransactionManager transactionManager = new JdbcMiniTransactionManager(dataSource);
        MiniTransactionStatus outer = transactionManager.begin(MiniPropagation.REQUIRED);

        MiniTransactionStatus inner = transactionManager.begin(MiniPropagation.NESTED);

        // REQUIRES_NEW 테스트(requiresNewSuspendsTheExistingTransactionAndUsesAFreshConnection)와
        // 정확히 대비되는 지점 - NESTED는 새 Connection을 얻지 않는다.
        assertThat(inner.isNewTransaction()).isFalse();
        assertThat(inner.getConnection()).isSameAs(outer.getConnection());

        transactionManager.commit(inner);
        transactionManager.commit(outer);
    }

    @Test
    void nestedRollbackDoesNotMarkTheOuterTransactionRollbackOnlyUnlikeRequired() {
        JdbcMiniTransactionManager transactionManager = new JdbcMiniTransactionManager(dataSource);
        MiniTransactionStatus outer = transactionManager.begin(MiniPropagation.REQUIRED);
        MiniTransactionStatus inner = transactionManager.begin(MiniPropagation.NESTED);

        transactionManager.rollback(inner);

        // REQUIRED 참여자였다면 이 rollback()이 holder를 rollback-only로 표시해서, 아래
        // owner의 commit()이 MiniUnexpectedRollbackException을 던졌을 것이다
        // (participantFailureMarksRollbackOnlySoOwnerCommitRollsBackAndThrows 참고).
        // NESTED는 savepoint까지만 되돌리고 holder는 건드리지 않으므로, outer는 이 실패를
        // 전혀 모른 채 정상적으로 commit()할 수 있다.
        transactionManager.commit(outer); // 예외 없이 끝나야 한다
    }

    @Test
    void interceptorCommitsASuccessfulTransferThroughTheFullProxy() throws SQLException {
        JdbcMiniTransactionManager transactionManager = new JdbcMiniTransactionManager(dataSource);
        JdbcAccountRepository repository = new JdbcAccountRepository(transactionManager);
        MiniProxyFactory factory = new MiniProxyFactory(repository);
        factory.addInterceptor(new MiniTransactionInterceptor(transactionManager, MiniPropagation.REQUIRED));
        Account account = factory.getProxy();

        account.transfer(1, 10);

        assertThat(readBalance(1)).isEqualTo(110);
    }

    @Test
    void interceptorRollsBackAFailingTransferThroughTheFullProxy() throws SQLException {
        JdbcMiniTransactionManager transactionManager = new JdbcMiniTransactionManager(dataSource);
        JdbcAccountRepository repository = new JdbcAccountRepository(transactionManager);
        MiniProxyFactory factory = new MiniProxyFactory(repository);
        factory.addInterceptor(new MiniTransactionInterceptor(transactionManager, MiniPropagation.REQUIRED));
        Account account = factory.getProxy();

        assertThatThrownBy(() -> account.transfer(1, -1000)).isInstanceOf(IllegalStateException.class);

        assertThat(readBalance(1)).isEqualTo(100);
    }

    @Test
    void ownerRollbackUndoesAParticipantsAlreadyAppliedChangesBecauseTheyShareOneConnection() throws SQLException {
        JdbcMiniTransactionManager transactionManager = new JdbcMiniTransactionManager(dataSource);
        JdbcAccountRepository repository = new JdbcAccountRepository(transactionManager);
        MiniProxyFactory accountFactory = new MiniProxyFactory(repository);
        accountFactory.addInterceptor(new MiniTransactionInterceptor(transactionManager, MiniPropagation.REQUIRED));
        Account account = accountFactory.getProxy();

        AccountFacadeImpl facadeTarget = new AccountFacadeImpl(account);
        MiniProxyFactory facadeFactory = new MiniProxyFactory(facadeTarget);
        facadeFactory.addInterceptor(new MiniTransactionInterceptor(transactionManager, MiniPropagation.REQUIRED));
        AccountFacade facade = facadeFactory.getProxy();

        // 두 번째 이체가 잔액을 마이너스로 만들어 실패한다. facade가 그 예외를 삼키지 않고
        // 그대로 전파하므로, facade 자신의 인터셉터(owner)도 그 예외를 보고 rollback()을
        // "직접" 호출한다 - 이 경로는 commit()의 rollback-only 검사(5단계)를 거치지 않는다.
        // 그래도 안전한 이유는 같은 물리적 커넥션을 공유해서 owner의 rollback()이 첫 번째
        // 이체까지 함께 되돌리기 때문이다.
        assertThatThrownBy(() -> facade.transferTwice(1, 10, -1000)).isInstanceOf(IllegalStateException.class);

        assertThat(readBalance(1)).isEqualTo(100);
    }

    @Test
    void participantFailureMarksRollbackOnlySoOwnerCommitRollsBackAndThrows() throws SQLException {
        JdbcMiniTransactionManager transactionManager = new JdbcMiniTransactionManager(dataSource);
        JdbcAccountRepository repository = new JdbcAccountRepository(transactionManager);
        MiniProxyFactory accountFactory = new MiniProxyFactory(repository);
        accountFactory.addInterceptor(new MiniTransactionInterceptor(transactionManager, MiniPropagation.REQUIRED));
        Account account = accountFactory.getProxy();

        AccountFacadeImpl facadeTarget = new AccountFacadeImpl(account);
        MiniProxyFactory facadeFactory = new MiniProxyFactory(facadeTarget);
        facadeFactory.addInterceptor(new MiniTransactionInterceptor(transactionManager, MiniPropagation.REQUIRED));
        AccountFacade facade = facadeFactory.getProxy();

        // 이번엔 facade가 두 번째 이체의 실패를 삼킨다 - facade 입장에서는 정상적으로
        // 리턴하는 것처럼 보인다. 5단계(rollback-only 전파)가 없었다면(2단계까지처럼
        // 참여자의 rollback()이 아무것도 안 했다면) owner의 commit()은 이 실패를 전혀 모른
        // 채 그대로 커밋해서, 첫 번째 이체만 반영되는 데이터 정합성 버그로 이어졌을 것이다.
        assertThatThrownBy(() -> facade.transferTwiceSwallowingFailures(1, 10, -1000))
                .isInstanceOf(MiniUnexpectedRollbackException.class);

        assertThat(readBalance(1)).isEqualTo(100);
    }

    @Test
    void requiresNewCommitsIndependentlyEvenWhenTheOuterTransactionLaterFails() throws SQLException {
        JdbcMiniTransactionManager transactionManager = new JdbcMiniTransactionManager(dataSource);
        JdbcAccountRepository repository = new JdbcAccountRepository(transactionManager);
        MiniProxyFactory accountFactory = new MiniProxyFactory(repository);
        accountFactory.addInterceptor(new MiniTransactionInterceptor(transactionManager, MiniPropagation.REQUIRES_NEW));
        Account requiresNewAccount = accountFactory.getProxy();

        MiniTransactionStatus outer = transactionManager.begin(MiniPropagation.REQUIRED);
        requiresNewAccount.transfer(1, 10);
        // outer는 이 이체가 이미 독립적으로 커밋된 뒤에 실패한다.
        transactionManager.rollback(outer);

        // REQUIRES_NEW로 커밋된 변경은 outer의 롤백과 무관하게 그대로 남는다.
        assertThat(readBalance(1)).isEqualTo(110);
    }

    @Test
    void nestedParticipantFailureDoesNotPreventTheOwnerFromCommittingNormallyUnlikeRequired() throws SQLException {
        // participantFailureMarksRollbackOnlySoOwnerCommitRollsBackAndThrows와 완전히 같은
        // 시나리오(facade가 두 번째 이체의 실패를 삼킴)를, account 프록시의 전파 속성만
        // REQUIRED -> NESTED로 바꿔서 재현한다 - 그것만으로 결과가 "예외 + 잔액 100 그대로"에서
        // "예외 없음 + 잔액 110"으로 뒤바뀐다.
        JdbcMiniTransactionManager transactionManager = new JdbcMiniTransactionManager(dataSource);
        JdbcAccountRepository repository = new JdbcAccountRepository(transactionManager);
        MiniProxyFactory accountFactory = new MiniProxyFactory(repository);
        accountFactory.addInterceptor(new MiniTransactionInterceptor(transactionManager, MiniPropagation.NESTED));
        Account account = accountFactory.getProxy();

        AccountFacadeImpl facadeTarget = new AccountFacadeImpl(account);
        MiniProxyFactory facadeFactory = new MiniProxyFactory(facadeTarget);
        facadeFactory.addInterceptor(new MiniTransactionInterceptor(transactionManager, MiniPropagation.REQUIRED));
        AccountFacade facade = facadeFactory.getProxy();

        // 첫 번째 이체(+10)는 NESTED savepoint가 정상적으로 해제(release)되며 성공한다.
        // 두 번째 이체(-1000)는 잔액 부족으로 실패해 savepoint까지만 롤백되지만, 그 실패는
        // outer(facade 자신의 REQUIRED 트랜잭션)를 rollback-only로 오염시키지 않는다.
        facade.transferTwiceSwallowingFailures(1, 10, -1000); // 예외 없이 정상 반환돼야 한다

        assertThat(readBalance(1)).isEqualTo(110); // 첫 번째 이체만 반영됨
    }

    @Test
    void synchronizationCallbacksFireInOrderOnCommit() {
        JdbcMiniTransactionManager transactionManager = new JdbcMiniTransactionManager(dataSource);
        List<String> events = new ArrayList<>();
        MiniTransactionStatus status = transactionManager.begin(MiniPropagation.REQUIRED);

        transactionManager.registerSynchronization(new MiniTransactionSynchronization() {
            @Override
            public void beforeCommit() {
                events.add("beforeCommit");
            }

            @Override
            public void afterCommit() {
                events.add("afterCommit");
            }
        });

        transactionManager.commit(status);

        assertThat(events).containsExactly("beforeCommit", "afterCommit");
    }

    @Test
    void synchronizationCallbackFiresOnRollback() {
        JdbcMiniTransactionManager transactionManager = new JdbcMiniTransactionManager(dataSource);
        List<String> events = new ArrayList<>();
        MiniTransactionStatus status = transactionManager.begin(MiniPropagation.REQUIRED);

        transactionManager.registerSynchronization(new MiniTransactionSynchronization() {
            @Override
            public void afterRollback() {
                events.add("afterRollback");
            }
        });

        transactionManager.rollback(status);

        assertThat(events).containsExactly("afterRollback");
    }
}

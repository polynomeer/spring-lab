package lab.minispring.autoproxy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import lab.minispring.container.BeanDefinition;
import lab.minispring.container.SimpleBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MiniAutoProxyCreatorTest {

    private SimpleBeanFactory buildBeanFactory(List<String> transactionLog) {
        MiniAdvisor advisor = new MiniAdvisor(
                new MiniTransactionalPointcut(), new MiniTransactionInterceptor(transactionLog));

        SimpleBeanFactory beanFactory = new SimpleBeanFactory();
        beanFactory.addBeanPostProcessor(new MiniAutoProxyCreator(advisor));
        beanFactory.registerBeanDefinition("account", new BeanDefinition(AccountServiceImpl.class));
        beanFactory.registerBeanDefinition("notifier", new BeanDefinition(NotifierImpl.class));
        return beanFactory;
    }

    @Test
    void eligibleBeanIsReplacedByAProxy() {
        SimpleBeanFactory beanFactory = buildBeanFactory(new ArrayList<>());

        Account account = beanFactory.getBean(Account.class);

        assertThat(account).isNotInstanceOf(AccountServiceImpl.class);
    }

    @Test
    void transactionalMethodIsWrappedWithBeginAndCommit() {
        List<String> transactionLog = new ArrayList<>();
        SimpleBeanFactory beanFactory = buildBeanFactory(transactionLog);
        Account account = beanFactory.getBean(Account.class);

        account.transfer("bob", 10);

        assertThat(account.balance()).isEqualTo(90);
        assertThat(transactionLog).containsExactly("BEGIN transfer", "COMMIT transfer");
    }

    @Test
    void nonTransactionalMethodIsNotWrappedEvenThoughTheBeanIsProxied() {
        List<String> transactionLog = new ArrayList<>();
        SimpleBeanFactory beanFactory = buildBeanFactory(transactionLog);
        Account account = beanFactory.getBean(Account.class);

        account.balance();

        assertThat(transactionLog).isEmpty();
    }

    @Test
    void failedTransferRollsBackAndRethrowsTheOriginalException() {
        List<String> transactionLog = new ArrayList<>();
        SimpleBeanFactory beanFactory = buildBeanFactory(transactionLog);
        Account account = beanFactory.getBean(Account.class);

        assertThatThrownBy(() -> account.transfer("bob", -5))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(transactionLog).containsExactly("BEGIN transfer", "ROLLBACK transfer");
    }

    @Test
    void beanWithoutAnyTransactionalMethodIsNotProxied() {
        SimpleBeanFactory beanFactory = buildBeanFactory(new ArrayList<>());

        Notifier notifier = beanFactory.getBean(Notifier.class);

        assertThat(notifier).isInstanceOf(NotifierImpl.class);
    }
}

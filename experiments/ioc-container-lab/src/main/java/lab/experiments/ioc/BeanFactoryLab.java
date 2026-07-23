package lab.experiments.ioc;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

public class BeanFactoryLab {

    public static void main(String[] args) {
        DefaultListableBeanFactory beanFactory =
                new DefaultListableBeanFactory();

        RootBeanDefinition paymentDefinition =
                new RootBeanDefinition(PaymentService.class);

        beanFactory.registerBeanDefinition(
                "paymentService",
                paymentDefinition
        );

        PaymentService paymentService =
                beanFactory.getBean(PaymentService.class);

        paymentService.pay();
    }
}

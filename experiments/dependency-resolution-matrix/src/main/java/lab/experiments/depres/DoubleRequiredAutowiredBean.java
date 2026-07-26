package lab.experiments.depres;

import org.springframework.beans.factory.annotation.Autowired;

// @Autowired(required=true, 기본값)가 두 생성자에 붙으면 AutowiredAnnotationBeanPostProcessor
// #determineCandidateConstructors가 즉시 BeanCreationException을 던진다.
public class DoubleRequiredAutowiredBean {

    @Autowired
    public DoubleRequiredAutowiredBean() {
    }

    @Autowired
    public DoubleRequiredAutowiredBean(Dependency dependency) {
    }
}

package lab.experiments.depres;

import org.springframework.beans.factory.annotation.Autowired;

// 생성자가 여러 개면 @Autowired가 붙은 것이 선택된다.
public class AutowiredSelectedBean {

    private final Dependency dependency;

    public AutowiredSelectedBean() {
        this.dependency = null;
    }

    @Autowired
    public AutowiredSelectedBean(Dependency dependency) {
        this.dependency = dependency;
    }

    public Dependency getDependency() {
        return dependency;
    }
}

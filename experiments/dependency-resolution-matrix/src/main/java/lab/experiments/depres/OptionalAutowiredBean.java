package lab.experiments.depres;

import org.springframework.beans.factory.annotation.Autowired;

// @Autowired(required=false) 생성자는 후보 목록에 들어가되, 기본 생성자가 있으면 그게
// 대체(fallback) 후보로 함께 등록된다. 의존성이 없으면 기본 생성자로 떨어진다.
public class OptionalAutowiredBean {

    private final Dependency dependency;

    public OptionalAutowiredBean() {
        this.dependency = null;
    }

    @Autowired(required = false)
    public OptionalAutowiredBean(Dependency dependency) {
        this.dependency = dependency;
    }

    public Dependency getDependency() {
        return dependency;
    }
}

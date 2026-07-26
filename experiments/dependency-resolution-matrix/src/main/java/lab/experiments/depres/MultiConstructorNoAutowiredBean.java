package lab.experiments.depres;

// 생성자가 여러 개인데 @Autowired가 하나도 없으면 determineCandidateConstructors()는
// 후보를 전혀 정하지 못한다(EMPTY_CONSTRUCTOR_ARRAY) - 일반적인 인스턴스화 경로로 넘어가서
// 기본 생성자만 쓰인다. Dependency가 등록돼 있어도 주입되지 않는다.
public class MultiConstructorNoAutowiredBean {

    private final Dependency dependency;

    public MultiConstructorNoAutowiredBean() {
        this.dependency = null;
    }

    public MultiConstructorNoAutowiredBean(Dependency dependency) {
        this.dependency = dependency;
    }

    public Dependency getDependency() {
        return dependency;
    }
}

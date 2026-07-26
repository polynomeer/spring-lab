package lab.experiments.depres;

// 생성자가 하나뿐이면 @Autowired 없이도 그 생성자가 주입 대상이 된다.
public class SingleConstructorBean {

    private final Dependency dependency;

    public SingleConstructorBean(Dependency dependency) {
        this.dependency = dependency;
    }

    public Dependency getDependency() {
        return dependency;
    }
}

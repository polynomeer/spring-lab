package lab.experiments.depres;

// @Qualifier도 @Primary도 없다 - 생성자 파라미터 이름(secondaryGreeter)이 후보 빈 이름과
// 그대로 일치하는 것만으로 모호성을 해소할 수 있는지 확인하기 위한 대상.
// (-parameters 컴파일 옵션이 없으면 파라미터 이름이 "arg0"류로 지워져 이 실험 자체가 불가능하다.)
public class NameMatchedGreeterConsumer {

    private final Greeter secondaryGreeter;

    public NameMatchedGreeterConsumer(Greeter secondaryGreeter) {
        this.secondaryGreeter = secondaryGreeter;
    }

    public Greeter getGreeter() {
        return secondaryGreeter;
    }
}

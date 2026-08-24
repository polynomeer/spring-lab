package lab.experiments.placeholder;

// XML <property name="label" value="${greeting}"/> 스타일의 "고전적인" 프로퍼티
// 주입을 흉내내기 위한 평범한 자바빈 - 세터가 있어야 MutablePropertyValues로
// 주입할 수 있다.
public class Widget {

    private String label;

    public void setLabel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

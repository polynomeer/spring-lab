package lab.ext.timing;

public interface OrderService {

    // JDK 동적 프록시는 인터페이스 Method 객체를 통해 디스패치하므로, 애노테이션은 구현
    // 클래스가 아니라 여기(인터페이스)에 붙어 있어야 프록시가 인식할 수 있다.
    @MeasureTime
    void placeOrder();

    String cachedLookup();

    // 애노테이션이 없다 - 내부에서 placeOrder()를 this로 호출하는지, 프록시를 거쳐
    // 호출하는지에 따라 측정 여부가 갈리는 것을 보여주기 위한 진입점이다.
    void checkout();
}

package lab.minispring.aop;

public class CalculatorImpl implements Calculator {

    @Override
    public int add(int a, int b) {
        return a + b;
    }

    @Override
    public int divide(int a, int b) {
        return a / b;
    }
}

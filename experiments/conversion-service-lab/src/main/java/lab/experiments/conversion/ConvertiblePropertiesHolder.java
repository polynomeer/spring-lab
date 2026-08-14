package lab.experiments.conversion;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;

public class ConvertiblePropertiesHolder {

    @Value("${app.point}")
    private Point point;

    @Value("${app.numbers}")
    private List<Integer> numbers;

    public Point point() {
        return point;
    }

    public List<Integer> numbers() {
        return numbers;
    }
}

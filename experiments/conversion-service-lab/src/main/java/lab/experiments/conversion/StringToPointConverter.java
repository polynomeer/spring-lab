package lab.experiments.conversion;

import org.springframework.core.convert.converter.Converter;

public class StringToPointConverter implements Converter<String, Point> {

    @Override
    public Point convert(String source) {
        String[] parts = source.split(",");
        return new Point(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
    }
}

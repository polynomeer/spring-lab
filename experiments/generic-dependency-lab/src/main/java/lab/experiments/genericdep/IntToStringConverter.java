package lab.experiments.genericdep;

public class IntToStringConverter implements Converter<Integer, String> {

    @Override
    public String convert(Integer source) {
        return String.valueOf(source);
    }
}

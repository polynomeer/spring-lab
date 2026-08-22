package lab.experiments.genericdep;

public class StringToIntConverter implements Converter<String, Integer> {

    @Override
    public Integer convert(String source) {
        return Integer.parseInt(source);
    }
}

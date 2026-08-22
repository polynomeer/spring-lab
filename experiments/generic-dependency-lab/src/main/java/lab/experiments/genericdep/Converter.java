package lab.experiments.genericdep;

public interface Converter<S, T> {
    T convert(S source);
}

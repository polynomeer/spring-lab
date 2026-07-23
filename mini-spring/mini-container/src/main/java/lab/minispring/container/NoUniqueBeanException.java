package lab.minispring.container;

import java.util.List;

public class NoUniqueBeanException extends RuntimeException {

    public NoUniqueBeanException(Class<?> type, List<String> candidateNames) {
        super("Expected a single bean of type '" + type.getName() + "' but found "
                + candidateNames.size() + ": " + candidateNames);
    }
}

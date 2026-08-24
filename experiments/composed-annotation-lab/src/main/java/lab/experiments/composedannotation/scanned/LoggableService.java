package lab.experiments.composedannotation.scanned;

import lab.experiments.composedannotation.Loggable;

// @Component를 직접 붙이지 않았다 - 오직 @Loggable(메타 애노테이션으로 @Component를
// 붙인 합성 애노테이션)만 있다.
@Loggable
public class LoggableService {
}

package lab.tools.jdi;

/**
 * {@link TracerServer}가 표준 입력으로 받는 NDJSON 명령 한 줄. {@code cmd}는
 * {@code "step"}/{@code "play"}/{@code "pause"}/{@code "quit"} 중 하나이고,
 * {@code intervalMs}는 {@code "play"}일 때만 의미가 있다(생략 시 서버의 마지막 값 유지).
 */
public record TracerCommand(String cmd, Long intervalMs) {

    public static final String STEP = "step";
    public static final String PLAY = "play";
    public static final String PAUSE = "pause";
    public static final String QUIT = "quit";
}

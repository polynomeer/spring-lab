package lab.minispring.event;

public interface MiniEventListener<E> {

    void onEvent(E event);
}

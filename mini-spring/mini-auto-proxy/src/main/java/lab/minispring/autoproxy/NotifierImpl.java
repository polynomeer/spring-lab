package lab.minispring.autoproxy;

// @MiniTransactional이 붙은 메서드가 하나도 없다 - MiniAutoProxyCreator가 손대지 않고
// 그대로 둬야 하는 대상.
public class NotifierImpl implements Notifier {

    @Override
    public void notify(String message) {
    }
}

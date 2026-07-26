package lab.minispring.autoproxy;

public interface Account {

    @MiniTransactional
    void transfer(String to, int amount);

    int balance();
}

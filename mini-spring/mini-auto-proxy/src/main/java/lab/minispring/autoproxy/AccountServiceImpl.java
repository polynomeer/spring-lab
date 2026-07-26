package lab.minispring.autoproxy;

public class AccountServiceImpl implements Account {

    private int balance = 100;

    @Override
    public void transfer(String to, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        balance -= amount;
    }

    @Override
    public int balance() {
        return balance;
    }
}

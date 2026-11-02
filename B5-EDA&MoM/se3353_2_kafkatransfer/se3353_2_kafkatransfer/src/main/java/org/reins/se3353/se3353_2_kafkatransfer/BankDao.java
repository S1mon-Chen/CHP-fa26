package org.reins.se3353.se3353_2_kafkatransfer;

public interface BankDao {
    public void withdraw(String from, int amount);
    public void deposit(String to, int amount);
}

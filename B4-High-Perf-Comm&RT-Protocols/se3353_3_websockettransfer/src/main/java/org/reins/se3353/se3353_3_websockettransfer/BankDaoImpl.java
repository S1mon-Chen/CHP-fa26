package org.reins.se3353.se3353_3_websockettransfer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class BankDaoImpl implements BankDao {

    @Autowired
    private BankRepository bankRepository;

    @Transactional
    public void withdraw(String from, int amount) {
        Bank out = bankRepository.findById(from).get();
        out.setBalance(out.getBalance() - amount);
//         int result = 10 / 0;
    };

    @Transactional //(propagation = Propagation.REQUIRES_NEW)
    public void deposit(String to, int amount) {
        Bank in = bankRepository.findById(to).get();
        in.setBalance(in.getBalance() + amount);
//        int result = 10 / 0;
    };
}

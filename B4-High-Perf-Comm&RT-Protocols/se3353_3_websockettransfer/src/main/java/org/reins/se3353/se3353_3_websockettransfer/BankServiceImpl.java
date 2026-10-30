package org.reins.se3353.se3353_3_websockettransfer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BankServiceImpl implements BankService{

    @Autowired
    private BankDao bankDao;

    @Override
    @Transactional
    public void transfer(String from, String to, int amount){
        System.out.println(from + " " + to + amount);
        bankDao.withdraw(from, amount);
//        int result = 10 / 0;
        try {
            bankDao.deposit(to, amount);
        }catch (Exception e){
            e.printStackTrace();
        }
//        int result = 10 / 0;
    };
}

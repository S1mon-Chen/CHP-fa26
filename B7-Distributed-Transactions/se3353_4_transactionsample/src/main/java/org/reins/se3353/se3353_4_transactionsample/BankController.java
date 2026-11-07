package org.reins.se3353.se3353_4_transactionsample;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BankController {
    @Autowired
    private BankService bankService;

    @GetMapping(value = "/transfer")
    public String transfer() {
        System.out.println("Transferring...");
        bankService.transfer("Tom", "Jerry", 80);
        return "Done";
    }

}

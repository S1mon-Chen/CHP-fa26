package org.reins.se3353.se3353_2_kafkatransfer;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BankRepository extends JpaRepository<Bank, String> {
}
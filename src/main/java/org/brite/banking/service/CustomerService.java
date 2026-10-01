package org.brite.banking.service;

import lombok.extern.slf4j.Slf4j;
import org.brite.banking.domain.Address;
import org.brite.banking.domain.Customer;
import org.brite.banking.messages.BankingMessages;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Slf4j
@Service
public class CustomerService {
    private Customer client;
    public Customer getCustomer(){
        log.debug(BankingMessages.LOG_CUSTOMER_SAMPLE_RETURNED);
        Address address = Address.builder().street("111").city("Leander").state("Tx").zip("78717").country("USA").addressLine1("Leafvillage").addressLine2("Unit1").build();
        return Customer.builder().firstName("MM").lastName("DD").address(address).dateOfBirth(LocalDate.of(1990, 1, 1)).build();
    }
}

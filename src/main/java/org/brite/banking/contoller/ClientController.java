package org.brite.banking.contoller;

import org.brite.banking.domain.Customer;
import org.brite.banking.service.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/client")
public class ClientController {

    @Autowired
    private CustomerService clientService;

    @GetMapping("/name")
    public @ResponseBody Customer getClient(){
        return clientService.getCustomer();
    }
}

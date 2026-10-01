package org.brite.banking.component;

import org.brite.banking.domain.Account;
import org.brite.banking.domain.AccountType;
import org.brite.banking.domain.Address;
import org.brite.banking.domain.Customer;
import org.brite.banking.request.AccountRegistrationRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface AccountMapper {
    AccountMapper INSTANCE = Mappers.getMapper(AccountMapper.class);

    // Target fields are nested expressions
    @Mapping(target = "accountType", source = "accountType", qualifiedByName = "stringToEnum")
    @Mapping(target = "customer", source = "request")
    @Mapping(target = "checkingAccountNumber", ignore = true)
    @Mapping(target = "savingAccountNumber", ignore = true)
    @Mapping(target = "checkingBalance", ignore = true)
    @Mapping(target = "savingBalance", ignore = true)
    Account toAccountEntity(AccountRegistrationRequest request);

    @Mapping(target = "address", source = "request")
    Customer toCustomerEntity(AccountRegistrationRequest request);

    @Mapping(target = "street", source = "street")
    @Mapping(target = "city", source = "city")
    @Mapping(target = "state", source = "state")
    @Mapping(target = "zip", source = "zip")
    @Mapping(target = "addressLine1", source = "addressLine1")
    @Mapping(target = "addressLine2", source = "addressLine2")
    @Mapping(target = "country", ignore = true) // Handled by @Builder.Default in entity
    Address toAddressEntity(AccountRegistrationRequest request);

    @Named("stringToEnum")
    default AccountType stringToEnum(String accountType) {
        if (accountType == null) return null;
        return AccountType.valueOf(accountType.toUpperCase());
    }
}

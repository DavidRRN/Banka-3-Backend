package rs.raf.bank_service.service;

import feign.FeignException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import rs.raf.bank_service.client.UserClient;
import rs.raf.bank_service.domain.dto.*;
import rs.raf.bank_service.domain.entity.*;
import rs.raf.bank_service.domain.enums.*;
import rs.raf.bank_service.domain.mapper.AccountMapper;
import rs.raf.bank_service.exceptions.*;
import rs.raf.bank_service.repository.AccountRepository;
import rs.raf.bank_service.repository.ChangeLimitRequestRepository;
import rs.raf.bank_service.repository.CurrencyRepository;
import rs.raf.bank_service.specification.AccountSearchSpecification;
import rs.raf.bank_service.utils.JwtTokenUtil;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class AccountService {
    private final CurrencyRepository currencyRepository;
    private final AccountRepository accountRepository;
    private final ChangeLimitRequestRepository changeLimitRequestRepository;
    private final JwtTokenUtil jwtTokenUtil;
    @Autowired
    private final UserClient userClient;

    @Operation(summary = "Retrieve accounts with filtering and pagination", description = "Returns a paginated list of accounts filtered by account number and owner's first and last name.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Accounts retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "No accounts found matching the criteria")
    })
    public Page<AccountDto> getAccounts(
            @Parameter(description = "Filter accounts by part of the account number", example = "111111111111111111") String accountNumber,
            @Parameter(description = "Filter accounts by owner's first name", example = "Marko") String firstName,
            @Parameter(description = "Filter accounts by owner's last name", example = "Markovic") String lastName,
            Pageable pageable) {

        Specification<Account> spec = Specification
                .where(AccountSearchSpecification.accountNumberContains(accountNumber));
        List<Account> accounts = accountRepository.findAll(spec);

        List<AccountDto> accountDtos = accounts.stream().map(account -> {
            ClientDto client = userClient.getClientById(account.getClientId());
            return AccountMapper.toDto(account, client);
        }).collect(Collectors.toList());

        if (firstName != null && !firstName.isEmpty()) {
            accountDtos = accountDtos.stream()
                    .filter(dto -> dto.getOwner() != null &&
                            dto.getOwner().getFirstName() != null &&
                            dto.getOwner().getFirstName().toLowerCase()
                                    .contains(firstName.toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (lastName != null && !lastName.isEmpty()) {
            accountDtos = accountDtos.stream()
                    .filter(dto -> dto.getOwner() != null &&
                            dto.getOwner().getLastName() != null &&
                            dto.getOwner().getLastName().toLowerCase()
                                    .contains(lastName.toLowerCase()))
                    .collect(Collectors.toList());
        }

        accountDtos.sort(Comparator
                .comparing(dto -> dto.getOwner() != null && dto.getOwner().getLastName() != null
                        ? dto.getOwner().getLastName()
                        : ""));

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), accountDtos.size());
        List<AccountDto> pageContent = accountDtos.subList(start, end);
        return new PageImpl<>(pageContent, pageable, accountDtos.size());
    }

    public void createNewBankAccount(NewBankAccountDto newBankAccountDto, String authorizationHeader) {
        Long userId = newBankAccountDto.getClientId();
        ClientDto clientDto = userClient.getClientById(userId);
        if (clientDto == null)
            throw new ClientNotFoundException(userId);
        Account newAccount;
        if (newBankAccountDto.getAccountType().equals(AccountOwnerType.COMPANY.toString())) {
            newAccount = new CompanyAccount();
            ((CompanyAccount) newAccount).setCompanyId(newBankAccountDto.getCompanyId());
        } else
            newAccount = new PersonalAccount();

        newAccount.setClientId(newBankAccountDto.getClientId());
        newAccount.setCreatedByEmployeeId(newBankAccountDto.getEmployeeId());
        newAccount.setCreationDate(LocalDate.ofEpochDay(Instant.now().getEpochSecond()));
        System.out.println(newBankAccountDto.getCurrency());
        Currency currCurrency = currencyRepository.findByCode(newBankAccountDto.getCurrency())
                .orElseThrow(() -> new CurrencyNotFoundException(newBankAccountDto.getCurrency()));
        newAccount.setCurrency(currCurrency);
        newAccount.setStatus(AccountStatus.valueOf(newBankAccountDto.getIsActive()));
        newAccount.setType(AccountType.valueOf(newBankAccountDto.getAccountType()));
        newAccount.setAccountOwnerType(AccountOwnerType.valueOf(newBankAccountDto.getAccountOwnerType()));
        newAccount.setBalance(newBankAccountDto.getInitialBalance());
        newAccount.setAvailableBalance(newBankAccountDto.getInitialBalance());
        newAccount.setDailyLimit(newBankAccountDto.getDailyLimit());
        newAccount.setMonthlyLimit(newBankAccountDto.getMonthlyLimit());
        newAccount.setDailySpending(newBankAccountDto.getDailySpending());
        newAccount.setMonthlySpending(newBankAccountDto.getMonthlySpending());

        String random = String.format("%09d", ThreadLocalRandom.current().nextInt(0, 1_000_000_000));
        String accountOwnerTypeNumber = "";
        switch (newBankAccountDto.getAccountOwnerType()) {
            case "PERSONAL" -> accountOwnerTypeNumber = "11";
            case "COMPANY" -> accountOwnerTypeNumber = "12";
            case "SAVINGS" -> accountOwnerTypeNumber = "13";
            case "RETIREMENT" -> accountOwnerTypeNumber = "14";
            case "YOUTH" -> accountOwnerTypeNumber = "15";
            case "STUDENT" -> accountOwnerTypeNumber = "16";
            case "UNEMPLOYED" -> accountOwnerTypeNumber = "17";
        }

        String accountNumber = "3330001" + random + accountOwnerTypeNumber;
        newAccount.setAccountNumber(accountNumber);

        accountRepository.save(newAccount);
    }

    public List<AccountDto> getMyAccounts(Long clientId) {
        try {
            ClientDto clientDto = userClient.getClientById(clientId);

            return accountRepository.findAllByClientId(clientId).stream().map(account ->
                    AccountMapper.toDto(account, clientDto)).sorted(Comparator.comparing(AccountDto::getAvailableBalance,
                    Comparator.nullsLast(Comparator.naturalOrder())).reversed()).collect(Collectors.toList());
        } catch (FeignException.NotFound e) {
            throw new UserNotAClientException();
        }
    }

    public AccountDetailsDto getAccountDetails(Long clientId, String accountNumber) {
        try {
            ClientDto clientDto = userClient.getClientById(clientId);
            Account account = accountRepository.findByAccountNumber(accountNumber)
                    .orElseThrow(AccountNotFoundException::new);

            if (!clientDto.getId().equals(account.getClientId()))
                throw new ClientNotAccountOwnerException();

            AccountDetailsDto accountDetailsDto;

            if (account.getAccountOwnerType() != AccountOwnerType.COMPANY) {
                accountDetailsDto = AccountMapper.toDetailsDto(account);
                accountDetailsDto.setAccountOwner(clientDto.getFirstName() + " " + clientDto.getLastName());
            } else {
                accountDetailsDto = AccountMapper.toCompanyDetailsDto(account);
                CompanyAccountDetailsDto companyAccountDetailsDto = (CompanyAccountDetailsDto) accountDetailsDto;

                CompanyAccount companyAccount = (CompanyAccount) account;
                CompanyDto companyDto = userClient.getCompanyById(companyAccount.getCompanyId());

                companyAccountDetailsDto.setCompanyName(companyDto.getName());
                companyAccountDetailsDto.setRegistrationNumber(companyDto.getRegistrationNumber());
                companyAccountDetailsDto.setTaxId(companyDto.getTaxId());
                companyAccountDetailsDto.setAddress(companyDto.getAddress());
            }
            return accountDetailsDto;
        } catch (FeignException.NotFound e) {
            throw new UserNotAClientException();
        }
    }

    //Verovatno ce da ide u TransactionService ali ne znam jer nemam Transaction entitet
    public void getAccountTransactions(Long clientId, String accountNumber) {
        try {
            ClientDto clientDto = userClient.getClientById(clientId);
            Account account = accountRepository.findByAccountNumber(accountNumber)
                    .orElseThrow(AccountNotFoundException::new);

            if (!clientDto.getId().equals(account.getClientId()))
                throw new ClientNotAccountOwnerException();

            //Nemam entitet za transakciju tako da ovde stajem
        } catch (FeignException.NotFound e) {
            throw new UserNotAClientException();
        }
    }


    public void changeAccountName(Long accountId, String newName) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccNotFoundException("Account not found"));

        System.out.println(">>> Account found: Current Name = " + account.getAccountNumber());

        // Ako je novo ime isto kao staro, nema potrebe za promenom
        if (account.getAccountNumber().equals(newName)) {
            System.out.println(">>> New name is the same as the current name. No changes made.");
            return;
        }

        // Proveravamo postoji li ime već u bazi
        boolean exists = accountRepository.existsByAccountNumberAndClientId(newName, account.getClientId());
        System.out.println(">>> Checking if account name '" + newName + "' already exists for client ID " + account.getClientId() + ": " + exists);

        if (exists) {
            System.out.println(">>> ERROR: Account name '" + newName + "' is already in use for client ID " + account.getClientId());
            throw new DuplicateAccountNameException("Account name already in use");
        }

        System.out.println(">>> Changing account name from '" + account.getAccountNumber() + "' to '" + newName + "'");
        account.setAccountNumber(newName);
        accountRepository.save(account);

        System.out.println(">>> SUCCESS: Account name changed to '" + newName + "'");
    }

    public void requestAccountLimitChange(Long accountId, String email, BigDecimal newLimit, String authHeader) {
        if (newLimit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Limit must be greater than zero");
        }

        log.info(">>> Checking if account with id {} exists...", accountId);
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccNotFoundException("Account not found"));


        Long clientId = jwtTokenUtil.getUserIdFromAuthHeader(authHeader);


        if (!account.getClientId().equals(clientId)) {
            throw new IllegalStateException("This account does not belong to the authenticated client.");
        }


        ChangeLimitRequest request = new ChangeLimitRequest(accountId, newLimit);
        changeLimitRequestRepository.save(request);


        VerificationRequestDto verificationRequest = VerificationRequestDto.builder()
                .userId(clientId)
                .email(email)
                .targetId(request.getId())
                .verificationType(VerificationType.CHANGE_LIMIT)
                .status(VerificationStatus.PENDING)
                .expirationTime(LocalDateTime.now().plusMinutes(5))
                .attempts(0)
                .build();

        userClient.createVerificationRequest(verificationRequest);

        log.info("Verification request created for client {}. Please approve to proceed.", clientId);
    }


    public void changeAccountLimit(Long requestId) {

        ChangeLimitRequest request = changeLimitRequestRepository
                .findById(requestId)
                .orElseThrow(() -> new IllegalStateException("No pending limit change request found"));


        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new AccNotFoundException("Account not found"));


        account.setDailyLimit(request.getNewLimit());
        accountRepository.save(account);


        request.setStatus(VerificationStatus.APPROVED);
        changeLimitRequestRepository.save(request);


        log.info("Account limit updated successfully for account {}", account.getAccountNumber());
    }

}

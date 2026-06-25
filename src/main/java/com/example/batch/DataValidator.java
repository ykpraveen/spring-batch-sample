package com.example.batch;

import com.example.entity.SalesData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class DataValidator {

    private static final Pattern TRANSACTION_ID_PATTERN = Pattern.compile("^[A-Z0-9\\-]+$");
    private static final Pattern TEXT_PATTERN = Pattern.compile("^[A-Za-z0-9\\s\\-]+$");

    public void validate(SalesData salesData) {
        if (salesData == null) {
            throw new ValidationException("Sales data must not be null", List.of("salesData must not be null"));
        }

        List<String> violations = new ArrayList<>();

        if (isBlank(salesData.getTransactionId())) {
            violations.add("transactionId: Transaction ID is required");
        } else if (!TRANSACTION_ID_PATTERN.matcher(salesData.getTransactionId()).matches()) {
            violations.add("transactionId: Transaction ID must use uppercase letters, numbers, and hyphens only");
        }

        if (salesData.getAmount() == null) {
            violations.add("amount: Amount is required");
        } else if (salesData.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            violations.add("amount: Amount must be greater than zero");
        }

        if (salesData.getQuantity() == null) {
            violations.add("quantity: Quantity is required");
        } else if (salesData.getQuantity() < 1) {
            violations.add("quantity: Quantity must be at least 1");
        }

        if (isBlank(salesData.getRegion())) {
            violations.add("region: Region is required");
        } else if (!TEXT_PATTERN.matcher(salesData.getRegion()).matches()) {
            violations.add("region: Region must contain only letters, numbers, spaces, and hyphens");
        }

        if (isBlank(salesData.getProductCategory())) {
            violations.add("productCategory: Product category is required");
        } else if (!TEXT_PATTERN.matcher(salesData.getProductCategory()).matches()) {
            violations.add("productCategory: Product category must contain only letters, numbers, spaces, and hyphens");
        }

        LocalDateTime createdAt = salesData.getCreatedAt();
        if (createdAt == null) {
            violations.add("createdAt: Created at timestamp is required");
        } else if (createdAt.isAfter(LocalDateTime.now())) {
            violations.add("createdAt: Created at must be in the past or present");
        }

        if (!violations.isEmpty()) {
            throw new ValidationException(
                    "Sales data failed validation",
                    violations.stream().sorted().toList()
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

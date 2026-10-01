package com.securebank.shared.application;

import com.securebank.shared.domain.InvalidValueException;
import java.util.List;

/** Página de resultados independente de framework (a camada application não conhece Spring Data). */
public record PageResult<T>(List<T> items, int page, int size, long totalElements) {

    public static final int MAX_SIZE = 100;
    private static final int MAX_PAGE = 100_000; // page * size precisa caber num int (offset)

    public static void validate(int page, int size) {
        if (page < 0 || page > MAX_PAGE || size < 1 || size > MAX_SIZE) {
            throw new InvalidValueException("page must be between 0 and " + MAX_PAGE + " and size between 1 and "
                    + MAX_SIZE);
        }
    }
}

package com.petcuidar.model;

import java.util.Locale;

public final class EmailCanonico {
    private EmailCanonico() {}

    public static String normalizar(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}

package com.petcuidar.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IaPropertiesTest {

    @Test
    void integracaoSemValoresPropriosUsaOsPadroesAtuais() {
        IaProperties props = new IaProperties();
        assertEquals(5000, props.timeoutMs(props.getLembrete()));
        assertEquals(5000, props.timeoutMs(props.getResumo()));
        assertEquals(5000, props.timeoutMs(props.getPredicao()));
        assertEquals(3, props.circuitBreaker(props.getResumo()).getMaxFalhas());
        assertEquals(30000, props.circuitBreaker(props.getResumo()).getResetMs());
    }

    @Test
    void integracaoSobrescreveSomenteOQueDefinir() {
        IaProperties props = new IaProperties();
        props.setTimeoutMs(4000);
        props.getCircuitBreaker().setResetMs(20000);
        props.getPredicao().setTimeoutMs(1500L);
        props.getPredicao().getCircuitBreaker().setMaxFalhas(5);

        assertEquals(1500, props.timeoutMs(props.getPredicao()));
        assertEquals(5, props.circuitBreaker(props.getPredicao()).getMaxFalhas());
        assertEquals(20000, props.circuitBreaker(props.getPredicao()).getResetMs());
        assertEquals(4000, props.timeoutMs(props.getLembrete()));
        assertEquals(3, props.circuitBreaker(props.getLembrete()).getMaxFalhas());
    }
}

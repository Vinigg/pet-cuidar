package com.petcuidar.config;

import com.petcuidar.service.ia.AnthropicLembreteIaClient;
import com.petcuidar.service.ia.LembreteIaClient;
import com.petcuidar.service.ia.LembreteIaSimulator;
import com.petcuidar.service.ia.OpenAiIaClient;
import com.petcuidar.service.ia.ResumoIaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

/**
 * Escolhe o provedor de IA e registra exatamente um {@link LembreteIaClient}
 * e um {@link ResumoIaClient}, qualquer que seja a combinacao de propriedades.
 *
 * <p>Regra de selecao:</p>
 * <ol>
 *   <li>{@code ia.provider} (variavel {@code LLM_PROVIDER}) define o provedor.</li>
 *   <li>A propriedade antiga {@code ia.llm.provider} so e considerada quando
 *       {@code ia.provider} e o simulador; se as duas apontarem provedores
 *       reais diferentes, vale {@code ia.provider} e um aviso e registrado.</li>
 *   <li>Valor desconhecido em qualquer uma delas impede a inicializacao com
 *       uma mensagem que lista os valores aceitos.</li>
 * </ol>
 *
 * <p>Os beans sao expostos como adaptadores de interface unica: assim o
 * simulador e o cliente {@code openai}, que implementam as duas portas, nunca
 * aparecem como candidatos duplicados na injecao.</p>
 */
@Configuration
public class IaClientConfig {

    private static final Logger log = LoggerFactory.getLogger(IaClientConfig.class);

    /** Provedores aceitos em {@code ia.provider}. */
    public enum ProvedorIa {
        SIMULADOR("simulador"), OPENAI("openai"), LLM_EXTERNO("llm-externo");

        private final String valor;

        ProvedorIa(String valor) { this.valor = valor; }

        public String valor() { return valor; }

        /** Converte o valor configurado; vazio ou ausente = {@code null}. */
        static ProvedorIa de(String configurado, String propriedade) {
            if (configurado == null || configurado.isBlank()) return null;
            String normalizado = configurado.strip().toLowerCase(Locale.ROOT);
            if (normalizado.equals("simulator")) return SIMULADOR; // grafia antiga do padrao
            for (ProvedorIa provedor : values()) {
                if (provedor.valor.equals(normalizado)) return provedor;
            }
            throw new IllegalStateException("Provedor de IA invalido em " + propriedade + ": '" + configurado.strip()
                    + "'. Valores aceitos: simulador, openai, llm-externo.");
        }
    }

    @Bean
    public ProvedorIa provedorIa(IaProperties props) {
        ProvedorIa provedor = resolver(props.getProvider(), props.getLlm().getProvider());
        log.info("Provedor de IA selecionado: {}", provedor.valor());
        return provedor;
    }

    @Bean
    public LembreteIaClient lembreteIaClient(ProvedorIa provedor, IaProperties props,
                                             @Value("${lembrete.nome-clinica:Pet Cuidar}") String nomeClinica) {
        return switch (provedor) {
            case SIMULADOR -> new LembreteIaSimulator()::gerar;
            case OPENAI -> new OpenAiIaClient(props)::gerar;
            case LLM_EXTERNO -> new AnthropicLembreteIaClient(props, nomeClinica);
        };
    }

    @Bean
    public ResumoIaClient resumoIaClient(ProvedorIa provedor, IaProperties props) {
        if (provedor == ProvedorIa.OPENAI) {
            return new OpenAiIaClient(props)::resumir;
        }
        if (provedor == ProvedorIa.LLM_EXTERNO) {
            log.info("O provedor llm-externo atende somente lembretes; o resumo do historico usa o simulador.");
        }
        return new LembreteIaSimulator()::resumir;
    }

    static ProvedorIa resolver(String provider, String providerLegado) {
        ProvedorIa principal = ProvedorIa.de(provider, "ia.provider (LLM_PROVIDER)");
        ProvedorIa legado = ProvedorIa.de(providerLegado, "ia.llm.provider");
        if (principal == null) principal = ProvedorIa.SIMULADOR;
        if (legado == null || legado == ProvedorIa.SIMULADOR || legado == principal) return principal;
        if (principal == ProvedorIa.SIMULADOR) return legado;
        log.warn("ia.provider={} e ia.llm.provider={} divergem; usando ia.provider.",
                principal.valor(), legado.valor());
        return principal;
    }
}

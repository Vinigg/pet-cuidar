package com.petcuidar.config;

import com.petcuidar.config.IaClientConfig.ProvedorIa;
import com.petcuidar.model.Consulta;
import com.petcuidar.model.Pet;
import com.petcuidar.model.Tutor;
import com.petcuidar.service.ia.AnthropicLembreteIaClient;
import com.petcuidar.service.ia.LembreteGeneratorService;
import com.petcuidar.service.ia.LembreteIaClient;
import com.petcuidar.service.ia.ResumoIaClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante exatamente um LembreteIaClient e um ResumoIaClient para qualquer
 * combinacao de ia.provider (LLM_PROVIDER) e da propriedade antiga ia.llm.provider.
 */
class IaClientConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(IaProperties.class, IaClientConfig.class);

    @Test
    void padraoSemPropriedadesUsaSimulador() {
        runner.run(ctx -> {
            assertUmClienteDeCada(ctx);
            assertThat(ctx.getBean(ProvedorIa.class)).isEqualTo(ProvedorIa.SIMULADOR);
            assertThat(ctx.getBean(LembreteIaClient.class).gerar(dados())).contains("Ana", "Rex");
            assertThat(ctx.getBean(ResumoIaClient.class).resumir("Rex", List.of(), ""))
                    .contains("Rex");
        });
    }

    @Test
    void grafiaAntigaSimulatorContinuaValida() {
        runner.withPropertyValues("ia.provider=simulator").run(ctx -> {
            assertUmClienteDeCada(ctx);
            assertThat(ctx.getBean(ProvedorIa.class)).isEqualTo(ProvedorIa.SIMULADOR);
        });
    }

    @Test
    void openaiRegistraUmClienteDeCada() {
        runner.withPropertyValues("ia.provider=openai").run(ctx -> {
            assertUmClienteDeCada(ctx);
            assertThat(ctx.getBean(ProvedorIa.class)).isEqualTo(ProvedorIa.OPENAI);
        });
    }

    @Test
    void llmExternoSemChaveSobeEUsaOTemplate() {
        runner.withPropertyValues("ia.provider=llm-externo", "ia.llm.api-key=").run(ctx -> {
            assertUmClienteDeCada(ctx);
            assertThat(ctx.getBean(ProvedorIa.class)).isEqualTo(ProvedorIa.LLM_EXTERNO);
            LembreteIaClient cliente = ctx.getBean(LembreteIaClient.class);
            assertThat(cliente).isInstanceOf(AnthropicLembreteIaClient.class);

            LembreteGeneratorService gerador = new LembreteGeneratorService(ctx.getBean(IaProperties.class), cliente);
            try {
                LembreteGeneratorService.ResultadoLembrete resultado = gerador.gerar(consulta());
                assertThat(resultado.usouFallback).isTrue();
                assertThat(resultado.textoValidado).contains("Ana", "Rex", "24/11/2026", "14:30");
            } finally {
                gerador.encerrarExecutor();
            }
        });
    }

    @Test
    void propriedadeAntigaLlmProviderAindaAtivaLlmExterno() {
        runner.withPropertyValues("ia.provider=simulador", "ia.llm.provider=llm-externo").run(ctx -> {
            assertUmClienteDeCada(ctx);
            assertThat(ctx.getBean(ProvedorIa.class)).isEqualTo(ProvedorIa.LLM_EXTERNO);
        });
    }

    @Test
    void combinacaoQueAntesRegistravaDoisClientesAgoraRegistraUm() {
        // ia.provider=simulator + ia.llm.provider externo: antes, dois LembreteIaClient e a app nao subia.
        runner.withPropertyValues("ia.provider=simulator", "ia.llm.provider=llm-externo").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertUmClienteDeCada(ctx);
        });
    }

    @Test
    void provedoresReaisDivergentesUsamIaProvider() {
        runner.withPropertyValues("ia.provider=openai", "ia.llm.provider=llm-externo").run(ctx -> {
            assertUmClienteDeCada(ctx);
            assertThat(ctx.getBean(ProvedorIa.class)).isEqualTo(ProvedorIa.OPENAI);
        });
        runner.withPropertyValues("ia.provider=llm-externo", "ia.llm.provider=openai").run(ctx -> {
            assertUmClienteDeCada(ctx);
            assertThat(ctx.getBean(ProvedorIa.class)).isEqualTo(ProvedorIa.LLM_EXTERNO);
        });
    }

    @Test
    void openaiComLlmProviderSimuladorContinuaOpenai() {
        runner.withPropertyValues("ia.provider=openai", "ia.llm.provider=simulador").run(ctx -> {
            assertUmClienteDeCada(ctx);
            assertThat(ctx.getBean(ProvedorIa.class)).isEqualTo(ProvedorIa.OPENAI);
        });
    }

    @Test
    void provedorInvalidoFalhaNaInicializacaoComMensagemClara() {
        runner.withPropertyValues("ia.provider=desconhecido").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).rootCause()
                    .hasMessageContaining("ia.provider")
                    .hasMessageContaining("desconhecido")
                    .hasMessageContaining("simulador, openai, llm-externo");
        });
    }

    @Test
    void provedorInvalidoNaPropriedadeAntigaTambemFalha() {
        runner.withPropertyValues("ia.llm.provider=xyz").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining("ia.llm.provider");
        });
    }

    private static void assertUmClienteDeCada(AssertableApplicationContext ctx) {
        assertThat(ctx).hasNotFailed();
        assertThat(ctx.getBeansOfType(LembreteIaClient.class)).hasSize(1);
        assertThat(ctx.getBeansOfType(ResumoIaClient.class)).hasSize(1);
    }

    private static com.petcuidar.service.ia.DadosLembrete dados() {
        return com.petcuidar.service.ia.DadosLembrete.de(consulta());
    }

    private static Consulta consulta() {
        Tutor tutor = new Tutor(); tutor.setNome("Ana");
        Pet pet = new Pet(); pet.setNome("Rex"); pet.setTutor(tutor);
        Consulta consulta = new Consulta(); consulta.setPet(pet);
        consulta.setDataHora(LocalDateTime.of(2026, 11, 24, 14, 30));
        return consulta;
    }
}

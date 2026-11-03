package com.petcuidar.service.ia;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validacao deterministica do texto gerado pela IA antes do envio.
 * A IA nunca e fonte de verdade: o texto so e aprovado se contiver,
 * exatamente, nome do tutor, nome do pet, data (dd/MM/yyyy) e horario
 * (HH:mm) persistidos, e se nao trouxer dados que o backend nao forneceu
 * (outros numeros, datas, horarios, telefones, links, e-mails, outro
 * veterinario) nem placeholders nao substituidos.
 *
 * Limite conhecido: afirmacoes em texto livre sem numeros (ex.: "traga a
 * carteira de vacinacao") nao sao detectaveis por regra; o prompt proibe
 * esse tipo de conteudo.
 */
@Component
public class LembreteGuardrail {

    public static final int TAMANHO_MAXIMO = 600;

    public enum Motivo {
        VAZIO, MUITO_LONGO, PLACEHOLDER, CONTATO_OU_LINK, TUTOR_AUSENTE, PET_AUSENTE,
        DATA_AUSENTE, HORARIO_AUSENTE, INFORMACAO_NAO_FORNECIDA, VETERINARIO_DIVERGENTE
    }

    private static final Pattern PLACEHOLDER = Pattern.compile(
            "[{}\\[\\]<>]|\\$\\{|\\bnull\\b|\\bundefined\\b|\\bN/A\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTATO = Pattern.compile("https?://|www\\.|@", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIGITO = Pattern.compile("\\d");
    private static final Pattern MENCAO_VETERINARIO = Pattern.compile(
            "\\bDra?\\.?\\s+(\\p{L}+)", Pattern.UNICODE_CHARACTER_CLASS);

    private final String nomeClinica;

    public LembreteGuardrail(@Value("${lembrete.nome-clinica:Pet Cuidar}") String nomeClinica) {
        this.nomeClinica = nomeClinica == null || nomeClinica.isBlank() ? null : nomeClinica.strip();
    }

    /** @return o motivo da rejeicao, ou vazio quando o texto esta aprovado. */
    public Optional<Motivo> avaliar(String texto, DadosLembrete dados) {
        if (texto == null || texto.isBlank()) return Optional.of(Motivo.VAZIO);
        if (texto.length() > TAMANHO_MAXIMO) return Optional.of(Motivo.MUITO_LONGO);
        if (PLACEHOLDER.matcher(texto).find()) return Optional.of(Motivo.PLACEHOLDER);
        if (CONTATO.matcher(texto).find()) return Optional.of(Motivo.CONTATO_OU_LINK);
        if (!texto.contains(dados.nomeTutor())) return Optional.of(Motivo.TUTOR_AUSENTE);
        if (!texto.contains(dados.nomePet())) return Optional.of(Motivo.PET_AUSENTE);
        if (!texto.contains(dados.dataFormatada())) return Optional.of(Motivo.DATA_AUSENTE);
        if (!texto.contains(dados.horaFormatada())) return Optional.of(Motivo.HORARIO_AUSENTE);
        if (contemNumeroNaoFornecido(texto, dados)) return Optional.of(Motivo.INFORMACAO_NAO_FORNECIDA);
        if (citaOutroVeterinario(texto, dados)) return Optional.of(Motivo.VETERINARIO_DIVERGENTE);
        return Optional.empty();
    }

    /** Qualquer digito alem dos dados fornecidos indica data, horario, telefone ou valor inventado. */
    private boolean contemNumeroNaoFornecido(String texto, DadosLembrete dados) {
        String restante = texto;
        for (String permitido : new String[]{dados.dataFormatada(), dados.horaFormatada(), dados.nomeTutor(),
                dados.nomePet(), dados.nomeVeterinario(), nomeClinica}) {
            if (permitido != null) restante = restante.replace(permitido, " ");
        }
        return DIGITO.matcher(restante).find();
    }

    private boolean citaOutroVeterinario(String texto, DadosLembrete dados) {
        String[] palavrasVeterinario = dados.possuiVeterinario()
                ? dados.nomeVeterinario().toLowerCase(Locale.ROOT).split("[\\s.]+")
                : new String[0];
        Matcher mencao = MENCAO_VETERINARIO.matcher(texto);
        while (mencao.find()) {
            String nome = mencao.group(1).toLowerCase(Locale.ROOT);
            if (Arrays.stream(palavrasVeterinario).noneMatch(nome::equals)) return true;
        }
        return false;
    }
}

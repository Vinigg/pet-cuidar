import { cpfValido, formatarCpf, formatarTelefone, somenteDigitos, telefoneValido, validarCadastro } from "./validacaoCadastro";

test("valida os dois digitos verificadores do CPF e rejeita numeros repetidos", () => {
  expect(cpfValido("529.982.247-25")).toBe(true);
  expect(cpfValido("52998224725")).toBe(true);
  ["", "529982247", "52998224724", "52998224715", "11111111111", "00000000000"].forEach((cpf) => {
    expect(cpfValido(cpf)).toBe(false);
  });
});

test("aplica mascaras progressivas e limita o comprimento", () => {
  expect(formatarCpf("52998224725")).toBe("529.982.247-25");
  expect(formatarCpf("529.982.247-2599")).toBe("529.982.247-25");
  expect(formatarCpf("5299")).toBe("529.9");
  expect(formatarCpf("")).toBe("");
  expect(formatarTelefone("1134567890")).toBe("(11) 3456-7890");
  expect(formatarTelefone("(11) 99999-888899")).toBe("(11) 99999-8888");
  expect(formatarTelefone("119")).toBe("(11) 9");
  expect(formatarTelefone("")).toBe("");
  expect(somenteDigitos("(11) 99999-8888")).toBe("11999998888");
});

test("valida telefones brasileiros com DDD", () => {
  expect(telefoneValido("(11) 3456-7890")).toBe(true);
  expect(telefoneValido("(21) 99999-8888")).toBe(true);
  ["123", "(00) 99999-8888", "(11) 88888-8888"].forEach((telefone) => {
    expect(telefoneValido(telefone)).toBe(false);
  });
});

test("valida email, campos em branco e idade sem tornar CPF e telefone obrigatorios", () => {
  const tutor = { nome: " Ana ", email: " ana@exemplo.com ", cpf: "", telefone: "" };
  expect(validarCadastro(tutor, [{ nome: "Luna", idade: "0" }])).toEqual({});
  expect(validarCadastro(tutor, [{ nome: "Luna", idade: "" }])).toEqual({});
  expect(validarCadastro({ ...tutor, nome: " ", email: "ana@" }, [{ nome: " ", idade: "-1" }])).toEqual({
    nome: expect.any(String), email: expect.any(String),
    "pets.0.nome": expect.any(String), "pets.0.idade": expect.any(String),
  });
  expect(validarCadastro(tutor, [{ nome: "Luna", idade: "1.5" }])["pets.0.idade"]).toBeDefined();
});
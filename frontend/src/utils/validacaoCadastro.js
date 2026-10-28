export const somenteDigitos = (valor) => valor.replace(/\D/g, "");

export const formatarCpf = (valor) => somenteDigitos(valor).slice(0, 11)
  .replace(/^(\d{3})(\d)/, "$1.$2")
  .replace(/^(\d{3})\.(\d{3})(\d)/, "$1.$2.$3")
  .replace(/\.(\d{3})(\d)/, ".$1-$2");

export const formatarTelefone = (valor) => {
  const digitos = somenteDigitos(valor).slice(0, 11);
  if (!digitos) return "";
  if (digitos.length <= 2) return `(${digitos}`;
  const numero = digitos.slice(2);
  const tamanhoPrefixo = numero.length > 8 ? 5 : 4;
  const telefone = numero.length > tamanhoPrefixo
    ? `${numero.slice(0, tamanhoPrefixo)}-${numero.slice(tamanhoPrefixo)}`
    : numero;
  return `(${digitos.slice(0, 2)}) ${telefone}`;
};

export const cpfValido = (valor) => {
  const digitos = somenteDigitos(valor);
  if (!/^\d{11}$/.test(digitos) || /^(\d)\1{10}$/.test(digitos)) return false;
  for (const tamanho of [9, 10]) {
    const soma = [...digitos.slice(0, tamanho)].reduce(
      (total, digito, indice) => total + Number(digito) * (tamanho + 1 - indice), 0
    );
    const resto = soma % 11;
    if (Number(digitos[tamanho]) !== (resto < 2 ? 0 : 11 - resto)) return false;
  }
  return true;
};

export const telefoneValido = (valor) => /^[1-9]{2}(?:[2-5]\d{7}|9\d{8})$/.test(somenteDigitos(valor));

export const validarCadastro = (tutor, pets) => {
  const erros = {};
  if (!tutor.nome.trim()) erros.nome = "Informe o nome do tutor.";
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(tutor.email.trim())) {
    erros.email = "Informe um e-mail valido, como nome@exemplo.com.";
  }
  if (tutor.cpf && !cpfValido(tutor.cpf)) erros.cpf = "Informe um CPF valido.";
  if (tutor.telefone && !telefoneValido(tutor.telefone)) {
    erros.telefone = "Informe DDD e telefone com 10 ou 11 digitos.";
  }
  pets.forEach((pet, indice) => {
    if (!pet.nome.trim()) erros[`pets.${indice}.nome`] = "Informe o nome do pet.";
    if (pet.idade !== "" && (!Number.isInteger(Number(pet.idade)) || Number(pet.idade) < 0)) {
      erros[`pets.${indice}.idade`] = "Informe a idade em anos inteiros, a partir de zero.";
    }
  });
  return erros;
};
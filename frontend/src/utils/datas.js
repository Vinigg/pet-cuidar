export const dataLocal = (valor = new Date()) => `${valor.getFullYear()}-${String(valor.getMonth() + 1).padStart(2, '0')}-${String(valor.getDate()).padStart(2, '0')}`;
export function diasDaSemana(offset = 0, referencia = new Date()) {
    const segunda = new Date(referencia.getFullYear(), referencia.getMonth(), referencia.getDate());
    segunda.setDate(segunda.getDate() - (segunda.getDay() + 6) % 7 + offset * 7);
    return Array.from({ length: 7 }, (_, i) => {
        const dia = new Date(segunda);
        dia.setDate(dia.getDate() + i);
        return { iso: dataLocal(dia), texto: dia.toLocaleDateString('pt-BR', { weekday: 'short', day: '2-digit', month: '2-digit' }), hoje: dataLocal(dia) === dataLocal(referencia) };
    });
}

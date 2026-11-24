package db.migration;

import com.petcuidar.model.EmailCanonico;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V7__normalizar_emails_de_tutores extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        var conexao = context.getConnection();
        try (var sql = conexao.createStatement()) {
            sql.execute("ALTER TABLE tutores ADD COLUMN email_normalizado TEXT");
        }

        try (var select = conexao.prepareStatement("SELECT id, email FROM tutores");
             var update = conexao.prepareStatement("UPDATE tutores SET email_normalizado = ? WHERE id = ?");
             var dados = select.executeQuery()) {
            while (dados.next()) {
                String email = dados.getString("email");
                update.setString(1, email == null ? null : EmailCanonico.normalizar(email));
                update.setLong(2, dados.getLong("id"));
                update.addBatch();
            }
            update.executeBatch();
        }

        try (var sql = conexao.createStatement()) {
            sql.execute("CREATE INDEX idx_tutores_email_normalizado ON tutores(email_normalizado)");
            sql.execute("""
                    DELETE FROM credenciais_auth WHERE conta_id IN (SELECT id FROM contas WHERE perfil = 'TUTOR')
                    """);
            sql.execute("DELETE FROM contas WHERE perfil = 'TUTOR'");
            sql.execute("""
                    INSERT INTO contas (email, nome, perfil, tutor_id)
                    SELECT t.email_normalizado, min(t.nome), 'TUTOR', min(t.id)
                    FROM tutores t
                    WHERE t.email_normalizado <> '' AND NOT EXISTS (
                        SELECT 1 FROM contas c WHERE c.email = t.email_normalizado
                    )
                    GROUP BY t.email_normalizado
                    HAVING count(*) = 1
                    """);
        }
    }
}

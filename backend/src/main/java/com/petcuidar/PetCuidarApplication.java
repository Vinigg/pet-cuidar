package com.petcuidar;

import com.petcuidar.migracao.MigracaoSqlitePostgres;
import org.postgresql.Driver;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.PrintStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Map;

@SpringBootApplication
@EnableScheduling
public class PetCuidarApplication {
    public static void main(String[] args) {
        if (args.length > 0 && "migrar-sqlite".equals(args[0])) {
            int resultado = migrarSqlite(args, System.getenv(), System.out, System.err);
            if (resultado != 0) {
                System.exit(resultado);
            }
            return;
        }
        SpringApplication.run(PetCuidarApplication.class, args);
    }

    static int migrarSqlite(String[] args, Map<String, String> ambiente, PrintStream saida, PrintStream erro) {
        if (args.length != 3) {
            erro.println("Uso: java -jar app.jar migrar-sqlite <copia.sqlite> <fuso-da-origem>");
            return 2;
        }

        String url = ambiente.getOrDefault("SPRING_DATASOURCE_URL", "");
        String usuario = ambiente.getOrDefault("SPRING_DATASOURCE_USERNAME", "");
        String senha = ambiente.getOrDefault("SPRING_DATASOURCE_PASSWORD", "");
        var propriedades = Driver.parseURL(url, null);
        if (propriedades == null || propriedades.containsKey("user") || propriedades.containsKey("password")
                || propriedades.containsKey("sslpassword") || usuario.isBlank() || senha.isBlank()) {
            erro.println("Configure a URL JDBC PostgreSQL e forneça usuário e senha em variáveis de ambiente separadas.");
            return 2;
        }

        try {
            var contagens = MigracaoSqlitePostgres.importar(Path.of(args[1]), ZoneId.of(args[2]), url, usuario, senha);
            contagens.forEach((tabela, total) -> saida.printf("%s: %d%n", tabela, total));
            saida.printf("Total importado: %d%n", contagens.values().stream().mapToLong(Long::longValue).sum());
            return 0;
        } catch (DateTimeException | InvalidPathException falha) {
            erro.println("Informe um caminho de arquivo e um fuso de origem válidos.");
            return 2;
        } catch (MigracaoSqlitePostgres.Falha falha) {
            erro.println(falha.getMessage());
            return 1;
        }
    }
}

"""Execute the PostgreSQL integration suite against a disposable TLS database."""

import argparse
from contextlib import closing, contextmanager
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import sqlite3
import subprocess
import tempfile
import time
from urllib.parse import quote
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[2]
TESTS = "EsquemaPostgresqlIT,ApiPostgresqlIT,MigracaoPostgresqlIT,ComandoMigracaoPostgresqlIT"
CERT_PATH = "/tmp/petcuidar-tls/server.crt"
START_POSTGRES = """
set -eu
umask 077
mkdir /tmp/petcuidar-tls
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj /CN=postgres-petcuidar-teste \\
  -addext subjectAltName=IP:127.0.0.1 \\
  -keyout /tmp/petcuidar-tls/server.key -out /tmp/petcuidar-tls/server.crt 2>/dev/null
chown -R postgres:postgres /tmp/petcuidar-tls
exec docker-entrypoint.sh postgres -c ssl=on \\
  -c ssl_key_file=/tmp/petcuidar-tls/server.key -c ssl_cert_file=/tmp/petcuidar-tls/server.crt
"""


def docker(*args, env=None, check=True):
    return subprocess.run(
        ["docker", *args], check=check, env=env, capture_output=True, text=True, timeout=45
    )


def esperar_postgres(container):
    limite = time.monotonic() + 35
    while time.monotonic() < limite:
        consulta = docker("exec", container, "pg_isready", "-q", "-h", "127.0.0.1",
                          "-U", "postgres", "-d", "petcuidar", check=False)
        if consulta.returncode == 0:
            return
        time.sleep(0.5)
    raise RuntimeError("PostgreSQL não ficou disponível. " + docker("logs", container).stderr[-1000:])


@contextmanager
def iniciar_api(java, jar, env, pasta):
    log = pasta / "api.log"
    with log.open("wb") as saida, subprocess.Popen(
        [java, "-Duser.timezone=America/Sao_Paulo", "-jar", str(jar)],
        cwd=pasta, env=env, stdout=saida, stderr=saida
    ) as processo:
        try:
            limite = time.monotonic() + 45
            while time.monotonic() < limite:
                texto = log.read_text(encoding="utf-8", errors="replace")
                porta = re.search(r"Tomcat started on port (\d+)", texto)
                if porta and "Started PetCuidarApplication" in texto:
                    yield int(porta.group(1))
                    return
                if processo.poll() is not None:
                    break
                time.sleep(0.2)
            raise AssertionError("Falha ao iniciar a API empacotada.\n" + texto[-1800:])
        finally:
            if processo.poll() is None:
                processo.terminate()
                processo.wait(timeout=15)


def historico_api(porta, pet, token):
    leitura = Request(f"http://127.0.0.1:{porta}/pets/{pet}/historico", headers={"Cookie": f"pc_sessao={token}"})
    with urlopen(leitura, timeout=8) as resposta:
        return json.load(resposta)


def verificar_jar(container, env, executor, pasta):
    subprocess.run([executor, "--batch-mode", "-f", "backend/pom.xml", "-DskipTests", "package"],
                   cwd=ROOT, env=env, check=True)
    jar, = (ROOT / "backend" / "target").glob("pet-cuidar-backend-*.jar")
    java_home = env.get("JAVA_HOME")
    java = str(Path(java_home) / "bin" / ("java.exe" if os.name == "nt" else "java")) if java_home else "java"
    app_env = {chave: valor for chave, valor in env.items() if not chave.startswith("SPRING_")}
    app_env.update(SERVER_ADDRESS="127.0.0.1", SERVER_PORT="0", AUTH_ADMIN_EMAIL="",
                   EMAIL_PROVIDER="local", LLM_PROVIDER="simulador", LEMBRETE_JOB_ENABLED="false")

    def psql(comando):
        return docker("exec", "-e", "PGPASSWORD", "-e", "PGSSLMODE=require", container,
                      "psql", "-X", "-w", "-h", "127.0.0.1", "-U", "postgres", "-d", "petcuidar",
                      "-v", "ON_ERROR_STOP=1", "-Atc", comando, env=env).stdout.strip()

    sql_dir = pasta / "origem"
    sql_dir.mkdir()
    origem = sql_dir / "petcuidar.db"
    token = secrets.token_urlsafe(32)
    digest = hashlib.sha256(token.encode("ascii")).hexdigest()
    expira = int(time.time()) + 3600

    with iniciar_api(java, jar, app_env, sql_dir) as porta:
        assert origem.is_file()
        with closing(sqlite3.connect(origem)) as sqlite:
            assert sqlite.execute("SELECT version FROM flyway_schema_history ORDER BY installed_rank").fetchall() == [
                (str(versao),) for versao in range(1, 8)
            ]
            print("PASS C2 API padrao inicia com SQLite", flush=True)

            limite = time.monotonic() + 8
            while time.monotonic() < limite:
                historico = sqlite.execute("""
                    SELECT h.pet_id, h.consulta_id, c.veterinario_id, h.id FROM historico_atendimentos h
                    JOIN consultas c ON c.id = h.consulta_id ORDER BY h.id LIMIT 1
                    """).fetchone()
                if historico:
                    break
                time.sleep(0.2)
            assert historico is not None, "A API SQLite não registrou os dados de demonstração."
            pet, consulta, veterinario, prontuario = historico
            conta = sqlite.execute("""
                INSERT INTO contas(email,nome,perfil,veterinario_id) VALUES
                    ('migracao@example.test','Veterinária de teste','VETERINARIO',?) RETURNING id
                """, (veterinario,)).fetchall()[0][0]
            sqlite.execute("INSERT INTO credenciais_auth(digest,conta_id,expira_em) VALUES(?,?,?)",
                           (digest, conta, expira))
            sqlite.commit()
            tabelas = [linha[0] for linha in sqlite.execute("""
                SELECT name FROM sqlite_schema WHERE type='table' AND name NOT GLOB 'sqlite_*'
                AND name NOT IN ('flyway_schema_history','credenciais_auth','desafios_email')
                """)]
            contagens = {nome: sqlite.execute(f"SELECT count(*) FROM {nome}").fetchone()[0] for nome in tabelas}
            assert len(contagens) == 10
        historico_origem = historico_api(porta, pet, token)

    psql("CREATE SCHEMA app_ensaio;")
    pg_env = dict(app_env, SPRING_PROFILES_ACTIVE="prod,postgres",
                  SPRING_DATASOURCE_URL=env["PETCUIDAR_TEST_POSTGRES_URL"] + "&currentSchema=app_ensaio",
                  SPRING_DATASOURCE_USERNAME=env["PETCUIDAR_TEST_POSTGRES_USER"],
                  SPRING_DATASOURCE_PASSWORD=env["PETCUIDAR_TEST_POSTGRES_PASSWORD"])
    migracao = [java, "-jar", str(jar), "migrar-sqlite", str(origem), "America/Sao_Paulo"]
    carga = subprocess.run(migracao, env=pg_env, capture_output=True, text=True,
                           encoding="utf-8", errors="replace", timeout=45)
    assert carga.returncode == 0, carga.stderr
    assert f"Total importado: {sum(contagens.values())}" in carga.stdout.splitlines()
    assert all(f"{tabela}: {total}" in carga.stdout.splitlines() for tabela, total in contagens.items())
    assert psql("SELECT version FROM app_ensaio.flyway_schema_history") == "1"

    psql(f"INSERT INTO app_ensaio.credenciais_auth(digest,conta_id,expira_em) VALUES('{digest}',{conta},{expira})")
    with iniciar_api(java, jar, pg_env, pasta) as porta:
        assert psql("SELECT count(*) > 0 FROM pg_stat_ssl WHERE ssl AND pid <> pg_backend_pid()") == "t"
        historico_destino = historico_api(porta, pet, token)
        for leitura in (historico_origem, historico_destino):
            item = next(c for c in leitura["consultas"] if c["consultaId"] == consulta)
            registro = next(a for a in item["atendimentos"] if a["id"] == prontuario)
            assert all(registro[campo] for campo in ("dataAtendimento", "dataCriacao", "dataAtualizacao"))
        assert historico_origem == historico_destino
        print("PASS C6 carga preserva horarios da API", flush=True)

    # O certificado confiável contém apenas o IP 127.0.0.1 como SAN.
    env_nome_errado = dict(pg_env, SPRING_DATASOURCE_URL=pg_env["SPRING_DATASOURCE_URL"].replace("127.0.0.1:", "localhost:"))
    hostname = subprocess.run([java, "-jar", str(jar)], env=env_nome_errado,
                              capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=45)
    assert hostname.returncode != 0 and "hostname localhost could not be verified" in (hostname.stdout + hostname.stderr), "Hostname inválido não foi rejeitado pelo pgJDBC."
    print("PASS C4 conexao valida certificado e hostname", flush=True)

    psql("ALTER TABLE app_ensaio.consultas ALTER COLUMN data_hora TYPE VARCHAR USING data_hora::VARCHAR")
    ddl = subprocess.run([java, "-jar", str(jar)], env=pg_env, capture_output=True,
                         text=True, encoding="utf-8", errors="replace", timeout=45)
    assert ddl.returncode != 0 and "Schema-validation: wrong column type encountered" in (ddl.stdout + ddl.stderr)
    print("PASS C1 API inicia com esquema PostgreSQL validado", flush=True)

    tabelas_sql = ", ".join(f"app_ensaio.{tabela}" for tabela in contagens)
    psql(f"""
        ALTER TABLE app_ensaio.consultas ALTER COLUMN data_hora TYPE TIMESTAMP USING data_hora::TIMESTAMP;
        TRUNCATE TABLE {tabelas_sql}, app_ensaio.credenciais_auth, app_ensaio.desafios_email;
        ALTER TABLE app_ensaio.tutores ADD CONSTRAINT tutor_teste_rejeitado CHECK (false);
        """)
    falha = subprocess.run(migracao, env=pg_env, capture_output=True, text=True,
                           encoding="utf-8", errors="replace", timeout=45)
    assert falha.returncode == 1 and "tabela tutores" in falha.stderr
    for processo in (carga, falha):
        mensagens = processo.stdout + processo.stderr
        assert pg_env["SPRING_DATASOURCE_PASSWORD"] not in mensagens
        assert "migracao@example.test" not in mensagens and "ana.lima@example.com" not in mensagens
        assert "Failing row contains" not in mensagens
    print("PASS C9 comando informa contagens sem expor valores", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="verifica o contêiner sem executar Maven")
    parser.add_argument("--test", help="executa apenas as classes informadas, sem abrir o JAR")
    args = parser.parse_args()

    env = dict(os.environ)
    env["POSTGRES_PASSWORD"] = secrets.token_hex(24)
    env["PGPASSWORD"] = env["POSTGRES_PASSWORD"]
    container = docker(
        "run", "-d", "--rm", "--pull", "never", "-p", "127.0.0.1::5432",
        "-e", "POSTGRES_PASSWORD", "-e", "POSTGRES_DB=petcuidar",
        "--entrypoint", "sh", "postgres:15", "-c", START_POSTGRES,
        env=env
    ).stdout.strip()

    try:
        esperar_postgres(container)
        if args.check:
            versao_tls = docker(
                "exec", "-e", "PGPASSWORD", "-e", "PGSSLMODE=require", container,
                "psql", "-X", "-w", "-h", "127.0.0.1", "-U", "postgres", "-d", "petcuidar",
                "-Atc", "SELECT current_setting('server_version_num'), ssl FROM pg_stat_ssl WHERE pid=pg_backend_pid()",
                env=env
            ).stdout.strip()
            if not versao_tls.startswith("15") or not versao_tls.endswith("|t"):
                raise RuntimeError("O banco de ensaio precisa usar PostgreSQL 15 com TLS.")
            print(f"PostgreSQL {versao_tls.split('|')[0]} com TLS ativo.")
            return 0

        executor = shutil.which("mvn.cmd" if os.name == "nt" else "mvn")
        if not executor:
            raise RuntimeError("Maven não encontrado no PATH.")
        address = docker("port", container, "5432/tcp").stdout.strip()
        port = address.rsplit(":", 1)[-1]
        with tempfile.TemporaryDirectory(prefix="petcuidar-pg-") as pasta:
            cert = Path(pasta) / "ca.crt"
            cert.write_text(docker("exec", container, "cat", CERT_PATH).stdout, encoding="ascii")
            env["PETCUIDAR_TEST_POSTGRES_URL"] = (
                f"jdbc:postgresql://127.0.0.1:{port}/petcuidar"
                f"?sslmode=verify-full&sslrootcert={quote(cert.as_posix(), safe='/:')}"
            )
            env["PETCUIDAR_TEST_POSTGRES_USER"] = "postgres"
            env["PETCUIDAR_TEST_POSTGRES_PASSWORD"] = env["POSTGRES_PASSWORD"]
            if args.test is None:
                verificar_jar(container, env, executor, Path(pasta))
            return subprocess.run(
                [executor, "--batch-mode", "-f", "backend/pom.xml", f"-Dtest={args.test or TESTS}", "test"],
                cwd=ROOT, env=env, check=False
            ).returncode
    finally:
        docker("stop", "-t", "3", container)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except subprocess.CalledProcessError as error:
        raise SystemExit((error.stderr or error.stdout or "Falha em uma operação Docker.").strip()) from None

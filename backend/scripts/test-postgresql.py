"""Execute the PostgreSQL integration suite against a disposable TLS database."""

import argparse
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import tempfile
import time
from urllib.parse import quote

ROOT = Path(__file__).resolve().parents[2]
TESTS = "EsquemaPostgresqlIT,ApiPostgresqlIT,MigracaoPostgresqlIT,ComandoMigracaoPostgresqlIT"
CERT_PATH = "/tmp/petcuidar-tls/server.crt"
START_POSTGRES = """
set -eu
umask 077
mkdir /tmp/petcuidar-tls
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj /CN=localhost \\
  -addext subjectAltName=DNS:localhost,IP:127.0.0.1 \\
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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="verifica o contêiner sem executar Maven")
    parser.add_argument("--test", default=TESTS, help="classes de teste a executar")
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
            return subprocess.run(
                [executor, "--batch-mode", "-f", "backend/pom.xml", f"-Dtest={args.test}", "test"],
                cwd=ROOT, env=env, check=False
            ).returncode
    finally:
        docker("stop", "-t", "3", container)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except subprocess.CalledProcessError as error:
        raise SystemExit((error.stderr or error.stdout or "Falha em uma operação Docker.").strip()) from None

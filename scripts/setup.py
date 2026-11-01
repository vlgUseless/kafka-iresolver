"""Install pinned iResolver and JDK archives locally, without changing system Java."""

import hashlib
import shutil
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
COMMIT = "2480ab354472e504a0825298023366b7f047eba2"
ARCHIVES = [
    (
        "iresolver.zip",
        "https://github.com/kovaloid/iresolver/releases/download/v1.1.0/iresolver.zip",
        "49637681877af1166754cccb44d06c166257da0d98ed970e104c4f3a3b84be76",
        "distribution",
        "distribution/iresolver/lib",
    ),
    (
        "jdk11.zip",
        "https://cdn.azul.com/zulu/bin/zulu11.92.109-ca-jdk11.0.33-win_x64.zip",
        "fdc77ff97cca003a15b680d0782f0de9063199bbb37b6a645e20bc3ccfcda323",
        "runtime",
        "runtime/zulu11.92.109-ca-jdk11.0.33-win_x64/bin/javac.exe",
    ),
]


def download(url, destination):
    print(f"Загрузка {destination.name}...", flush=True)
    temporary = destination.with_suffix(destination.suffix + ".part")
    try:
        with urllib.request.urlopen(url, timeout=180) as response:
            with temporary.open("wb") as output:
                shutil.copyfileobj(response, output)
        temporary.replace(destination)
    finally:
        temporary.unlink(missing_ok=True)


def extract_verified(archive_path, expected_hash, destination):
    with archive_path.open("rb") as source:
        actual_hash = hashlib.file_digest(source, "sha256").hexdigest()
    if actual_hash != expected_hash:
        raise ValueError(f"Неверный SHA-256 архива {archive_path}")
    destination.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive_path) as archive:
        for name in archive.namelist():
            if not (destination / name).resolve().is_relative_to(destination.resolve()):
                raise ValueError(f"Небезопасный путь в ZIP: {name}")
        archive.extractall(destination)


def main():
    downloads = ROOT / "downloads"
    downloads.mkdir(exist_ok=True)
    for name, url, checksum, directory, marker in ARCHIVES:
        if (ROOT / marker).exists():
            print(f"Уже установлено: {directory}")
            continue
        archive = downloads / name
        if not archive.exists():
            download(url, archive)
        extract_verified(archive, checksum, ROOT / directory)

    docs = ROOT / "docs"
    docs.mkdir(exist_ok=True)
    for name in ["cloudera-kafka-guide.pdf", "confluent-kafka-definitive-guide.pdf"]:
        if not (docs / name).exists():
            download(f"https://raw.githubusercontent.com/kovaloid/iresolver/{COMMIT}/docs/{name}",
                     docs / name)
    print("Зависимости готовы.")


if __name__ == "__main__":
    main()

"""Build iResolver datasets with physical PDF page numbers as document labels."""

from pathlib import Path
from pypdf import PdfReader

ROOT = Path(__file__).resolve().parents[1]


def main():
    dataset = []
    metadata = []
    files = []
    documents = sorted((ROOT / "docs").glob("*.pdf"))
    if not documents:
        raise FileNotFoundError("Нет PDF в docs; сначала выполните scripts/setup.py")

    for index, path in enumerate(documents):
        files.append(f"{index} {path.name}")
        reader = PdfReader(path)
        for number, page in enumerate(reader.pages, start=1):
            text = " ".join((page.extract_text() or "").replace("|", " ").split())
            if len(text) < 150:
                continue
            label = f"DOC_{index}_{number}"
            dataset.append(f"{label}|{text}")
            metadata.append(f"{label} {index} {number}")
        print(f"{path.name}: {len(reader.pages)} страниц", flush=True)

    output = ROOT / "data"
    output.mkdir(exist_ok=True)
    datasets = {
        "Documentation_DataSet.txt": dataset,
        "Documentation_Metadata.txt": metadata,
        "Documentation_List.txt": files,
    }
    for name, lines in datasets.items():
        (output / name).write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Проиндексировано страниц: {len(dataset)}")


if __name__ == "__main__":
    main()

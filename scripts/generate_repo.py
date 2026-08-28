import argparse
import gzip
import html
import json
from pathlib import Path

import index_pb2
from google.protobuf import json_format


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--apk-name", required=True)
    parser.add_argument("--jar-name", required=True)
    parser.add_argument("--signing-key", required=True)
    args = parser.parse_args()

    info = json.loads(args.metadata.read_text(encoding="utf-8"))
    args.output.mkdir(parents=True, exist_ok=True)

    apk_url = (
        f"https://github.com/{args.repository}/releases/download/"
        f"{args.tag}/{args.apk_name}"
    )
    icon_url = (
        f"https://raw.githubusercontent.com/{args.repository}/repo/icon.png"
    )

    extension = index_pb2.Extension(
        name=info["name"],
        packageName=info["packageName"],
        resources=index_pb2.Resources(apkUrl=apk_url, iconUrl=icon_url, jarUrl=jar_url),
        extensionLib=info["extensionLib"],
        versionCode=info["versionCode"],
        versionName=info["versionName"],
        contentWarning=info["contentWarning"],
        sources=[
            index_pb2.Source(
                id=int(source["id"]),
                name=source["name"],
                language=source["lang"],
                homeUrl=source["baseUrl"],
                mirrorUrls=source.get("mirrorUrls", []),
            )
            for source in info["sources"]
        ],
    )
    index = index_pb2.Index(
        name="Manga-Zaa",
        badgeLabel="MZ",
        signingKey=args.signing_key.lower().replace(":", ""),
        contact=index_pb2.Contact(
            website=f"https://github.com/{args.repository}",
        ),
        extensionList=index_pb2.ExtensionList(extensions=[extension]),
    )

    index_json = json_format.MessageToJson(
        index,
        always_print_fields_with_no_presence=False,
        preserving_proto_field_name=True,
    )
    (args.output / "index.json").write_text(index_json, encoding="utf-8")
    (args.output / "index.pb").write_bytes(
        gzip.compress(index.SerializeToString(deterministic=True), mtime=0)
    )

    escaped_url = html.escape(apk_url)
    escaped_name = html.escape(f"Tachiyomi: {info['name']}")
    (args.output / "index.html").write_text(
        "<!doctype html>\n<html><head><meta charset=\"utf-8\">"
        "<title>Manga-Zaa Extension</title></head><body><pre>"
        f"<a href=\"{escaped_url}\">{escaped_name}</a>"
        "</pre></body></html>\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()


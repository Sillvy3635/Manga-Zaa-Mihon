import argparse
import gzip
import html
import json
import shutil
from pathlib import Path

import index_pb2
from google.protobuf import json_format


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--module-dir", type=Path, action="append", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--signing-key", required=True)
    args = parser.parse_args()

    args.output.mkdir(parents=True, exist_ok=True)
    icons_dir = args.output / "icons"
    icons_dir.mkdir(exist_ok=True)

    extensions = []
    html_links = []
    for module_dir in args.module_dir:
        metadata = module_dir / "build" / "keiyoushi-source-info.json"
        info = json.loads(metadata.read_text(encoding="utf-8"))
        apks = sorted((module_dir / "build").rglob("*.apk"))
        jars = sorted((module_dir / "build" / "outputs" / "jar" / "release").glob("*.jar"))
        if not apks:
            raise FileNotFoundError(f"No APK found for {module_dir}")
        if not jars:
            raise FileNotFoundError(f"No JAR found for {module_dir}")

        apk_name = apks[0].name
        jar_name = jars[0].name
        apk_url = f"https://github.com/{args.repository}/releases/download/{args.tag}/{apk_name}"
        jar_url = f"https://github.com/{args.repository}/releases/download/{args.tag}/{jar_name}"
        icon_name = f"{info['packageName']}.png"
        icon_url = f"https://raw.githubusercontent.com/{args.repository}/repo/icons/{icon_name}"
        shutil.copy2(module_dir / "res" / "mipmap-xhdpi" / "ic_launcher.png", icons_dir / icon_name)

        extensions.append(
            index_pb2.Extension(
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
        )
        link_label = html.escape(f"Tachiyomi: {info['name']}")
        html_links.append(f'<a href="{html.escape(apk_url)}">{link_label}</a>')

    extensions.sort(key=lambda extension: extension.name.lower())
    index = index_pb2.Index(
        name="Sillvy Manga Extensions",
        badgeLabel="SM",
        signingKey=args.signing_key.lower().replace(":", ""),
        contact=index_pb2.Contact(
            website=f"https://github.com/{args.repository}",
        ),
        extensionList=index_pb2.ExtensionList(extensions=extensions),
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

    index_html = (
        "<!doctype html>\n<html><head><meta charset=\"utf-8\">"
        "<title>Sillvy Manga Extensions</title></head><body><pre>"
        + "\n".join(html_links)
        + "</pre></body></html>\n"
    )
    (args.output / "index.html").write_text(index_html, encoding="utf-8")


if __name__ == "__main__":
    main()

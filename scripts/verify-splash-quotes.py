"""Opt-in live check of bundled written excerpts. Never runs at app startup or in offline CI.

Only whitespace is normalized; spelling, case, and punctuation must match public HTML.
This checks exact text presence, not speaker eligibility or editorial context: those require
reviewing the original article and the role evidence listed in the catalogue.
"""
import concurrent.futures
import hashlib
from html.parser import HTMLParser
import json
from pathlib import Path
import re
import sys
import urllib.request
import urllib.error


class VisibleText(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.parts = []
        self.hidden = 0

    def handle_starttag(self, tag, attrs):
        if tag in ("script", "style"):
            self.hidden += 1

    def handle_endtag(self, tag):
        if tag in ("script", "style"):
            self.hidden = max(0, self.hidden - 1)

    def handle_data(self, data):
        if not self.hidden:
            self.parts.append(data)


def normalize(text):
    return " ".join(text.split())


def verify_source(entry):
    name, slug, quotes = entry
    url = f"https://giantbomb.com/articles/{slug}"
    request = urllib.request.Request(url, headers={"User-Agent": "LookTube quote source verification"})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            html = response.read(8 * 1024 * 1024).decode("utf-8")
    except (urllib.error.URLError, TimeoutError) as error:
        return {"catalogue_source": name, "url": url, "status": "unavailable", "error": str(error), "quotes": []}
    parser = VisibleText()
    parser.feed(html)
    # Joining without inserting spaces preserves words next to inline link boundaries.
    text = normalize("".join(parser.parts))
    spaced_text = normalize(" ".join(parser.parts))
    matches = [
        {"id": quote_id, "sha256": hashlib.sha256(quote.encode()).hexdigest(),
         "exact_text_found": normalize(quote) in text or normalize(quote) in spaced_text}
        for quote_id, quote in quotes
    ]
    return {"catalogue_source": name, "url": url, "status": "checked", "quotes": matches}


def main():
    root = Path(__file__).resolve().parents[1]
    code = (root / "app/src/main/java/com/looktube/app/LaunchIntroQuotes.kt").read_text(encoding="utf-8-sig")
    sources = dict(re.findall(r'private val (\w+) = source\("[^"]+", "([^"]+)", "[^"]+"\)', code))
    grouped = {name: [] for name in sources}
    for quote_id, quote, source_name in re.findall(r'LaunchIntroQuote\("([^"]+)", "([^"]+)", \w+, (\w+),', code):
        grouped[source_name].append((quote_id, quote))
    if sum(map(len, grouped.values())) < 21:
        raise RuntimeError("Could not parse the expanded quote catalogue")
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        results = list(pool.map(verify_source, ((name, sources[name], quotes) for name, quotes in grouped.items())))
    print(json.dumps({"sources": results}, indent=2, ensure_ascii=False))
    if any(result["status"] != "checked" for result in results):
        return 2
    return 0 if all(q["exact_text_found"] for result in results for q in result["quotes"]) else 1


if __name__ == "__main__":
    sys.exit(main())

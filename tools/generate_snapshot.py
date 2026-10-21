#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
مولّد اللقطة المضمّنة (snapshot) لقاعدة أماكن دليل اليمن.

المصدر: واجهة بيانات الخرائط العامة في OpenStreetMap
(https://api.openstreetmap.org/api/0.6/map) — بيانات مفتوحة بترخيص ODbL.

لكل مدينة نجلب مربعاً حول مركزها، ونستخرج العناصر المسمّاة القابلة للعرض
(محلات/خدمات) ونخزّنها في JSON صغير يوضع داخل التطبيق، فيعمل الدليل دون
إنترنت، وتُستخدم البيانات الحيّة لاحقاً عند توفّر الاتصال.

قاعدة «لا اختلاق»: لا يُضاف أي اسم أو رقم غير موجود في وسوم المصدر،
ولا يُصنّف عنصر لا يحمل وسماً يدل على كونه محلاً أو خدمة.
"""

import gzip
import json
import os
import sys
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET

API = "https://api.openstreetmap.org/api/0.6/map"
USER_AGENT = "DalilYemenApp/1.2 (+https://github.com/GoldAden/dalil-yemen; OSM data ODbL)"
DELAY_SECONDS = 1.0
RETRIES = 4
ROOT_HALF = 0.030
MIN_HALF = 0.008

CITIES = {
    "sanaa": (15.3539, 44.2059, "صنعاء"),
    "aden": (12.7896, 45.0285, "عدن"),
    "taiz": (13.5752, 44.0215, "تعز"),
    "hodeidah": (14.7979, 42.9545, "الحديدة"),
    "mukalla": (14.5425, 49.1231, "المكلا"),
    "ibb": (13.9702, 44.1779, "إب"),
    "dhamar": (14.5430, 44.4001, "ذمار"),
    "hajjah": (15.6942, 43.6058, "حجة"),
    "saada": (16.9409, 43.7630, "صعدة"),
    "marib": (15.4579, 45.3230, "مأرب"),
    "hadramout": (15.9441, 48.7926, "سيئون"),
    "lahj": (13.0578, 44.8836, "الحوطة"),
    "abyan": (13.1286, 45.3812, "زنجبار"),
    "shabwah": (14.5277, 46.8454, "عتق"),
    "albayda": (13.9881, 45.5729, "البيضاء"),
    "aldhale": (13.7017, 44.7314, "الضالع"),
    "aljawf": (16.1634, 44.7766, "الحزم"),
    "amran": (15.6628, 43.9430, "عمران"),
    "almahwit": (15.4701, 43.5448, "المحويت"),
    "raymah": (14.7104, 43.6082, "الجبين"),
    "socotra": (12.6512, 54.0237, "حديبو"),
}

KEEP_KEYS = (
    "name", "name:ar", "name:en", "int_name", "official_name",
    "shop", "amenity", "craft", "healthcare", "office", "leisure", "tourism",
    "phone", "contact:phone", "phone:mobile",
    "addr:full", "addr:street", "addr:housenumber", "addr:district",
    "addr:subdistrict", "addr:city",
    "opening_hours", "operator", "brand", "branch",
)

EXCLUDED_AMENITIES = {
    "parking", "parking_space", "bicycle_parking", "motorcycle_parking",
    "bus_station", "taxi", "toilets", "shelter", "bench", "waste_basket",
    "drinking_water", "fountain", "grave_yard", "place_of_worship",
    "police", "fire_station", "post_box", "recycling", "bicycle_rental",
    "clock", "atm", "vending_machine", "kindergarten", "school", "college",
    "university", "library", "townhall", "courthouse", "embassy", "prison",
    "ranger_station", "public_bath", "shower",
}

EXCLUDED_KEYS = {
    "highway", "railway", "waterway", "boundary", "admin_level", "natural",
    "landuse", "building", "building:part", "barrier", "power", "man_made",
    "route", "aeroway", "place", "water", "ford", "entrance",
}

CATEGORY_SOURCES = ("shop", "amenity", "craft", "healthcare", "office", "leisure", "tourism")

stats = {"requests": 0, "bytes": 0, "failed": 0, "split": 0, "skipped": 0}


def fetch_tile(lat, lon, half):
    """يجلب مربعاً واحداً. يعيد bytes، أو None إذا كان المربع أكبر من حدّ الخدمة."""
    url = "%s?bbox=%.6f,%.6f,%.6f,%.6f" % (
        API, lon - half, lat - half, lon + half, lat + half)
    request = urllib.request.Request(
        url, headers={"User-Agent": USER_AGENT, "Accept-Encoding": "gzip"})

    for attempt in range(RETRIES):
        try:
            stats["requests"] += 1
            with urllib.request.urlopen(request, timeout=240) as response:
                body = response.read()
                if response.headers.get("Content-Encoding") == "gzip":
                    body = gzip.decompress(body)
                stats["bytes"] += len(body)
                return body
        except urllib.error.HTTPError as error:
            message = error.read().decode("utf-8", "replace")
            if error.code == 400 and "too many nodes" in message:
                return None
            if error.code in (429, 500, 502, 503, 504) and attempt < RETRIES - 1:
                time.sleep(3 * (attempt + 1))
                continue
            print("    ! HTTP %d: %s" % (error.code, message.strip()[:120]))
            stats["failed"] += 1
            return b""
        except Exception as error:
            if attempt < RETRIES - 1:
                time.sleep(3 * (attempt + 1))
                continue
            print("    ! %s" % error)
            stats["failed"] += 1
            return b""


def useful(tags):
    if not any(tags.get(key) for key in CATEGORY_SOURCES):
        return False
    for key in EXCLUDED_KEYS:
        if tags.get(key):
            return False
    amenity = tags.get("amenity")
    if amenity and amenity in EXCLUDED_AMENITIES:
        return False
    if tags.get("shop") == "vacant" or tags.get("disused:shop") or tags.get("abandoned:shop"):
        return False
    if not (tags.get("name") or tags.get("name:ar") or tags.get("name:en")):
        return False
    return True


def parse(body, collected):
    root = ET.fromstring(body)
    nodes = {}
    for node in root.findall("node"):
        nodes[node.get("id")] = (float(node.get("lat")), float(node.get("lon")))

    for element in root:
        if element.tag not in ("node", "way"):
            continue
        tags = {tag.get("k"): tag.get("v") for tag in element.findall("tag")}
        if not useful(tags):
            continue

        if element.tag == "node":
            lat, lon = float(element.get("lat")), float(element.get("lon"))
        else:
            points = [nodes[ref.get("ref")] for ref in element.findall("nd")
                      if ref.get("ref") in nodes]
            if not points:
                continue
            lat = sum(point[0] for point in points) / len(points)
            lon = sum(point[1] for point in points) / len(points)

        kept = {key: tags[key] for key in KEEP_KEYS if tags.get(key)}
        collected["%s/%s" % (element.tag, element.get("id"))] = {
            "lat": round(lat, 6), "lon": round(lon, 6), "tags": kept}


def harvest(lat, lon, half, collected):
    time.sleep(DELAY_SECONDS)
    body = fetch_tile(lat, lon, half)

    if body is None:
        if half <= MIN_HALF:
            stats["skipped"] += 1
            print("      ~ مربع مزدحم تم تخطّيه عند %.4f,%.4f" % (lat, lon))
            return
        stats["split"] += 1
        quarter = half / 2
        for delta_lat in (-quarter, quarter):
            for delta_lon in (-quarter, quarter):
                harvest(lat + delta_lat, lon + delta_lon, quarter, collected)
        return

    if not body:
        return

    before = len(collected)
    parse(body, collected)
    print("      مربع %.4f,%.4f نصف=%.4f: %.1fMB، +%d عنصر"
          % (lat, lon, half, len(body) / 1048576.0, len(collected) - before))


def main():
    wanted = [arg for arg in sys.argv[1:] if arg in CITIES] or list(CITIES)
    collected = {}

    for city_id in wanted:
        lat, lon, label = CITIES[city_id]
        print("[%s] %s (%.4f, %.4f)" % (city_id, label, lat, lon))
        harvest(lat, lon, ROOT_HALF, collected)

    items = []
    for element_id, payload in collected.items():
        items.append({"id": element_id, "lat": payload["lat"],
                      "lon": payload["lon"], "t": payload["tags"]})
    items.sort(key=lambda item: item["id"])

    target = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                          "app", "src", "main", "assets", "places_snapshot.json")
    os.makedirs(os.path.dirname(target), exist_ok=True)

    with open(target, "w", encoding="utf-8") as handle:
        handle.write("{\n")
        handle.write('  "source": "OpenStreetMap (ODbL) - www.openstreetmap.org",\n')
        handle.write('  "generated": "%s",\n' % time.strftime("%Y-%m-%d"))
        handle.write('  "items": [\n')
        for index, item in enumerate(items):
            comma = "," if index < len(items) - 1 else ""
            handle.write("    %s%s\n" % (json.dumps(item, ensure_ascii=False,
                                                  separators=(",", ":")), comma))
        handle.write("  ]\n}\n")

    print("\nعدد الأماكن: %d | طلبات: %d | منقول: %.1f MB | تقسيمات: %d | متخطّى: %d | فاشل: %d"
          % (len(items), stats["requests"], stats["bytes"] / 1048576.0,
             stats["split"], stats["skipped"], stats["failed"]))
    print("الملف: %s (%.2f MB)" % (target, os.path.getsize(target) / 1048576.0))


if __name__ == "__main__":
    main()

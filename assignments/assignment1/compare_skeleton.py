"""对比两份 OpenAPI 文档的契约骨架，用于验证多次生成的结果是否一致。

用法: python3 compare_skeleton.py openapi.json openapi-round3.json

只比较影响接口契约的结构（路径、方法、状态码、schema 字段与约束），
不比较 description / example 等措辞类内容。
"""
import json
import sys
import unicodedata

CONSTRAINT_KEYS = ["type", "format", "enum", "nullable", "minimum", "maximum",
                   "minLength", "maxLength", "pattern", "default", "$ref", "not"]


def ref_of(content):
    return content.get("application/json", {}).get("schema", {}).get("$ref")


def operations(doc):
    ops = {}
    for path, item in doc["paths"].items():
        for method, op in item.items():
            ops[f"{method.upper()} {path}"] = op
    return ops


def constraints(prop):
    c = {k: prop[k] for k in CONSTRAINT_KEYS if k in prop}
    if "items" in prop:
        c["items"] = constraints(prop["items"])
    return c


def dimensions(doc):
    ops = operations(doc)
    schemas = doc["components"]["schemas"]
    return {
        "路径 + HTTP 方法": sorted(ops),
        "operationId": {k: v.get("operationId") for k, v in ops.items()},
        "security 声明": {k: v.get("security") for k, v in ops.items()},
        "查询/路径参数": {k: {p["name"]: (p["in"], p.get("required", False), constraints(p["schema"]))
                              for p in v.get("parameters", [])} for k, v in ops.items()},
        "请求体 schema": {k: ref_of(v["requestBody"]["content"]) if "requestBody" in v else None
                         for k, v in ops.items()},
        "响应状态码": {k: sorted(v["responses"]) for k, v in ops.items()},
        "响应体 schema": {k: {code: ref_of(r.get("content", {})) for code, r in v["responses"].items()}
                         for k, v in ops.items()},
        "schema 名称": sorted(schemas),
        "schema 必填字段": {n: sorted(s.get("required", [])) for n, s in schemas.items()},
        "schema 字段集合": {n: sorted(s.get("properties", {})) for n, s in schemas.items()},
        "字段类型与约束": {n: {p: constraints(v) for p, v in s.get("properties", {}).items()}
                          for n, s in schemas.items()},
    }


def diff(a, b, prefix=""):
    """返回 a、b 之间所有不同之处的路径列表。"""
    if isinstance(a, dict) and isinstance(b, dict):
        out = []
        for k in sorted(set(a) | set(b), key=str):
            if k not in a:
                out.append(f"{prefix}{k}: 仅出现在第二份")
            elif k not in b:
                out.append(f"{prefix}{k}: 仅出现在第一份")
            else:
                out += diff(a[k], b[k], f"{prefix}{k}.")
        return out
    return [] if a == b else [f"{prefix.rstrip('.')}: {json.dumps(a, ensure_ascii=False)} → {json.dumps(b, ensure_ascii=False)}"]


def pad(text, width):
    """按终端显示宽度补齐空格（中文字符占两列）。"""
    shown = sum(2 if unicodedata.east_asian_width(ch) in "WF" else 1 for ch in text)
    return text + " " * max(width - shown, 0)


def main(path_a, path_b):
    da, db = (dimensions(json.load(open(p, encoding="utf-8"))) for p in (path_a, path_b))
    print(f"对比: {path_a}  vs  {path_b}\n")
    print(f"{pad('对比维度', 26)}结果")
    print("-" * 44)
    details = {}
    for name in da:
        d = diff(da[name], db[name])
        print(f"{pad(name, 26)}{'一致' if not d else f'不一致（{len(d)} 处）'}")
        if d:
            details[name] = d
    for name, items in details.items():
        print(f"\n[{name}] 差异明细:")
        for line in items:
            print(f"  - {line}")


if __name__ == "__main__":
    main(*sys.argv[1:3])

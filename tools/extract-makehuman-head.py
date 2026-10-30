#!/usr/bin/env python3
"""Extract the head/neck only from MakeHuman's CC0 basemesh.

Usage: python tools/extract-makehuman-head.py base.obj output.obj
See docs/licenses/ and README for the pinned source and license. The original
OBJ has a full human body and helper geometry; neither is bundled here.
"""
import sys
from pathlib import Path

if len(sys.argv) != 3:
    raise SystemExit(__doc__)
source, output = map(Path, sys.argv[1:])
vertices = []
faces = []
body = False
for line in source.read_text(encoding="utf-8").splitlines():
    if line.startswith("v "):
        vertices.append(tuple(float(x) for x in line.split()[1:4]))
    elif line.startswith("g "):
        body = line.strip() == "g body"
    elif body and line.startswith("f "):
        face = [int(part.split("/")[0]) - 1 for part in line.split()[1:]]
        if all(vertices[index][1] >= 6.0 for index in face):
            faces.append(face)
used = sorted({index for face in faces for index in face})
remap = {old: new + 1 for new, old in enumerate(used)}
lines = [
    "# Head/neck excerpt from MakeHuman base.obj (CC0 1.0, September 2020).",
    "# Original: Data Collection AB, Joel Palmius, Jonas Hauquier.",
    "# See docs/licenses/makehuman-CC0.md and README for source and modifications.",
]
lines.extend("v %.4f %.4f %.4f" % vertices[index] for index in used)
for face in faces:
    # Triangle fan preserves the winding of the original OBJ's polygons.
    for index in range(1, len(face) - 1):
        lines.append("f %d %d %d" % (remap[face[0]], remap[face[index]], remap[face[index + 1]]))
output.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"Wrote {len(used)} vertices, {len(lines) - len(used) - 3} triangles to {output}")

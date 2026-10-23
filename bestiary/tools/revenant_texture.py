"""Paints the revenant soldier's 64x64 texture (vanilla humanoid UV layout: HumanoidModel.createMesh).

    python tools/revenant_texture.py src/main/resources/assets/bestiary/textures/entity/revenant_soldier.png

Grey-green dead skin, dark eye sockets, a worn gambeson and trousers under the chainmail the soldier wears; natural
colours only (the surface stays grounded, never glowing).
"""
import random
import sys

from PIL import Image

random.seed(26103)
img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
px = img.load()


def fill(x0, y0, x1, y1, base, jitter=10):
    for y in range(y0, y1):
        for x in range(x0, x1):
            j = random.randint(-jitter, jitter)
            px[x, y] = (max(0, min(255, base[0] + j)), max(0, min(255, base[1] + j)), max(0, min(255, base[2] + j)), 255)


SKIN = (118, 124, 104)
TUNIC = (74, 64, 54)
BELT = (44, 36, 30)
TROUSERS = (60, 54, 50)
BOOTS = (38, 33, 30)
SOCKET = (26, 24, 22)

# Head (0,0): every face skin, then the face's details.
fill(0, 0, 32, 16, SKIN, 12)
for x, y in [(9, 11), (10, 11), (13, 11), (14, 11), (9, 12), (14, 12)]:
    px[x, y] = SOCKET + (255,)
px[10, 12] = (60, 20, 18, 255)
px[13, 12] = (60, 20, 18, 255)
for x in range(10, 14):
    px[x, 14] = (40, 36, 32, 255)
px[11, 15] = (40, 36, 32, 255)
# Body (16,16): gambeson with a belt.
fill(16, 16, 40, 32, TUNIC, 8)
for x in range(16, 40):
    px[x, 29] = BELT + (255,)
    px[x, 30] = BELT + (255,)
for y in range(21, 29, 2):
    for x in range(20, 28, 3):
        px[x, y] = (62, 54, 46, 255)
# Arms (40,16): sleeves, then the dead hands.
fill(40, 16, 56, 32, TUNIC, 8)
fill(40, 26, 56, 32, SKIN, 12)
fill(48, 16, 52, 20, SKIN, 12)
# Legs (0,16): trousers and boots.
fill(0, 16, 16, 32, TROUSERS, 8)
fill(0, 28, 16, 32, BOOTS, 6)
fill(8, 16, 12, 20, BOOTS, 6)
img.save(sys.argv[1])
print("wrote", sys.argv[1])

"""A GIF from frozen-frame screenshots (one per tick): python tools/gif.py <out.gif> <width> <fps> <frame.png>...

The frames are scaled to <width> and played at <fps> (20 = real time for one frame per tick); the last frame holds.
"""
import sys

from PIL import Image


def main():
    out, width, fps = sys.argv[1], int(sys.argv[2]), float(sys.argv[3])
    frames = []
    for f in sorted(sys.argv[4:]):
        im = Image.open(f).convert("RGB")
        h = round(im.height * width / im.width)
        frames.append(im.resize((width, h), Image.LANCZOS).convert("P", palette=Image.ADAPTIVE, colors=128))
    durations = [round(1000 / fps)] * len(frames)
    durations[-1] = 600
    frames[0].save(out, save_all=True, append_images=frames[1:], duration=durations, loop=0, optimize=True)
    print(out, len(frames), "frames")


if __name__ == "__main__":
    main()

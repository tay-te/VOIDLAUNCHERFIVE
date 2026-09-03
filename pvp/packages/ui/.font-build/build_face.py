
import sys, json
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer
from fontTools.subset import Subsetter, Options, parse_unicodes

src, dest, axes_json, unicodes = sys.argv[1:5]
axes = json.loads(axes_json)
font = TTFont(src)
if axes:
    font = instancer.instantiateVariableFont(font, axes, inplace=False, updateFontNames=True)
options = Options()
options.layout_features = ['*']
options.name_IDs = ['*']
options.name_legacy = True
options.notdef_outline = True
options.recalc_bounds = True
options.drop_tables = []
subsetter = Subsetter(options=options)
subsetter.populate(unicodes=parse_unicodes(unicodes))
subsetter.subset(font)
font.flavor = 'woff2'
font.save(dest)

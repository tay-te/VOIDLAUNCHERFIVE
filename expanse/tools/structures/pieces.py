"""Pool element descriptions used by the designs and the generator."""


class Piece:
    """A template in a pool. `processors` names an entry of processors.PROCESSOR_LISTS (or None)."""

    def __init__(self, build, weight=1, processors=None, projection="rigid"):
        self.build, self.weight, self.processors, self.projection = build, weight, processors, projection


class Empty:
    def __init__(self, weight=1):
        self.weight = weight


class Feature:
    """A vanilla placed feature (e.g. minecraft:oak_checked) grown at an upward jigsaw."""

    def __init__(self, feature, weight=1):
        self.feature, self.weight = feature, weight

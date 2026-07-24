"""
Tests for the serialization utilities.

Covers serialize_value across its type branches, dataclass_to_dict (including
the exclude list and the non-dataclass guard), and the SerializableMixin.
"""

from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import List

import pytest

from service.utils.serialization import (
    serialize_value,
    dataclass_to_dict,
    SerializableMixin,
)


class Color(Enum):
    RED = "red"


@dataclass
class Point(SerializableMixin):
    x: int
    y: int


class TestSerializeValue:
    def test_none_passes_through(self):
        assert serialize_value(None) is None

    def test_enum_becomes_its_value(self):
        assert serialize_value(Color.RED) == "red"

    def test_datetime_becomes_isoformat(self):
        dt = datetime(2026, 7, 24, 12, 30, 0)
        assert serialize_value(dt) == dt.isoformat()

    def test_object_with_to_dict_is_delegated(self):
        assert serialize_value(Point(1, 2)) == {"x": 1, "y": 2}

    def test_list_is_serialized_recursively(self):
        assert serialize_value([Color.RED, 3]) == ["red", 3]

    def test_tuple_is_serialized_to_list(self):
        assert serialize_value((Color.RED, 3)) == ["red", 3]

    def test_dict_is_serialized_recursively(self):
        assert serialize_value({"c": Color.RED, "n": 1}) == {"c": "red", "n": 1}

    def test_plain_scalar_passes_through(self):
        assert serialize_value(42) == 42


class TestDataclassToDict:
    def test_serializes_all_fields(self):
        assert dataclass_to_dict(Point(1, 2)) == {"x": 1, "y": 2}

    def test_exclude_omits_named_fields(self):
        assert dataclass_to_dict(Point(1, 2), exclude=["y"]) == {"x": 1}

    def test_rejects_non_dataclass(self):
        with pytest.raises(TypeError):
            dataclass_to_dict({"x": 1})

    def test_rejects_dataclass_type_rather_than_instance(self):
        with pytest.raises(TypeError):
            dataclass_to_dict(Point)


class TestSerializableMixin:
    def test_to_dict_uses_serialization(self):
        @dataclass
        class Record(SerializableMixin):
            name: str
            color: Color
            tags: List[str] = field(default_factory=list)

        record = Record(name="a", color=Color.RED, tags=["x"])
        assert record.to_dict() == {"name": "a", "color": "red", "tags": ["x"]}

    def test_to_dict_respects_exclude(self):
        assert Point(1, 2).to_dict(exclude=["x"]) == {"y": 2}

    def test_get_serializable_fields_lists_field_names(self):
        assert Point.get_serializable_fields() == ["x", "y"]

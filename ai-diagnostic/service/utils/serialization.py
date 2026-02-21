"""
Serialization utilities.

Provides mixins and helpers for consistent JSON serialization of dataclasses.
"""

from dataclasses import fields, is_dataclass
from datetime import datetime
from enum import Enum
from typing import Any, Dict, List, Optional, Union


def serialize_value(value: Any) -> Any:
    """
    Serialize a value to JSON-compatible format.

    Handles:
    - Enum -> .value
    - datetime -> .isoformat()
    - dataclass with to_dict -> .to_dict()
    - list/tuple -> recursive serialization
    - dict -> recursive serialization
    - Other -> as-is
    """
    if value is None:
        return None
    if isinstance(value, Enum):
        return value.value
    if isinstance(value, datetime):
        return value.isoformat()
    if hasattr(value, 'to_dict') and callable(value.to_dict):
        return value.to_dict()
    if is_dataclass(value) and not isinstance(value, type):
        return dataclass_to_dict(value)
    if isinstance(value, (list, tuple)):
        return [serialize_value(v) for v in value]
    if isinstance(value, dict):
        return {k: serialize_value(v) for k, v in value.items()}
    return value


def dataclass_to_dict(obj: Any, exclude: Optional[List[str]] = None) -> Dict[str, Any]:
    """
    Convert a dataclass to a dictionary with proper serialization.

    Args:
        obj: A dataclass instance
        exclude: Optional list of field names to exclude

    Returns:
        Dictionary with serialized values
    """
    if not is_dataclass(obj) or isinstance(obj, type):
        raise TypeError(f"Expected dataclass instance, got {type(obj)}")

    exclude_set = set(exclude) if exclude else set()
    result = {}

    for field in fields(obj):
        if field.name in exclude_set:
            continue
        value = getattr(obj, field.name)
        result[field.name] = serialize_value(value)

    return result


class SerializableMixin:
    """
    Mixin that adds to_dict() method to dataclasses.

    Usage:
        @dataclass
        class MyData(SerializableMixin):
            name: str
            status: MyEnum
            created_at: datetime

        data = MyData(name="test", status=MyEnum.ACTIVE, created_at=datetime.now())
        data.to_dict()  # Returns properly serialized dict
    """

    def to_dict(self, exclude: Optional[List[str]] = None) -> Dict[str, Any]:
        """Convert this dataclass to a dictionary."""
        return dataclass_to_dict(self, exclude=exclude)

    @classmethod
    def get_serializable_fields(cls) -> List[str]:
        """Get list of field names that will be serialized."""
        if is_dataclass(cls):
            return [f.name for f in fields(cls)]
        return []

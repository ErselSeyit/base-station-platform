"""
Singleton pattern utilities.

Provides thread-safe singleton factory and decorator for service classes.
"""

import threading
from typing import TypeVar, Type, Optional, Callable

T = TypeVar('T')


class SingletonMeta(type):
    """
    Thread-safe singleton metaclass.

    Usage:
        class MyService(metaclass=SingletonMeta):
            pass

        instance = MyService()  # Always returns same instance
    """
    _instances: dict = {}
    _lock: threading.Lock = threading.Lock()

    def __call__(cls, *args, **kwargs):
        if cls not in cls._instances:
            with cls._lock:
                if cls not in cls._instances:
                    instance = super().__call__(*args, **kwargs)
                    cls._instances[cls] = instance
        return cls._instances[cls]


def singleton_factory(
    cls: Type[T],
    instance_holder: Optional[list] = None,
    lock: Optional[threading.Lock] = None
) -> Callable[[], T]:
    """
    Create a thread-safe singleton factory function.

    Args:
        cls: The class to create a singleton of
        instance_holder: Optional list to store instance (for external access)
        lock: Optional lock for thread safety

    Returns:
        A factory function that always returns the same instance

    Usage:
        class MyService:
            pass

        get_my_service = singleton_factory(MyService)
        service = get_my_service()
    """
    _instance: list = instance_holder if instance_holder is not None else [None]
    _lock = lock if lock is not None else threading.Lock()

    def get_instance() -> T:
        if _instance[0] is None:
            with _lock:
                if _instance[0] is None:
                    _instance[0] = cls()
        return _instance[0]

    return get_instance


def clear_singleton(cls: Type) -> None:
    """Clear a singleton instance (useful for testing)."""
    if cls in SingletonMeta._instances:
        with SingletonMeta._lock:
            SingletonMeta._instances.pop(cls, None)

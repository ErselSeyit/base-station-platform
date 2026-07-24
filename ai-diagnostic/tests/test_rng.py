"""
Tests for the shared RNG utilities.

The shared generator underpins reproducible simulation output, so the contract
under test is: the same seed reproduces the same sequence, and reset_rng swaps
the shared instance.
"""

import numpy as np
import pytest

from service.utils.rng import get_rng, reset_rng


@pytest.fixture(autouse=True)
def restore_default_seed():
    """Reseed the shared RNG afterwards so test ordering stays deterministic."""
    yield
    reset_rng()


def test_get_rng_returns_a_numpy_generator():
    assert isinstance(get_rng(), np.random.Generator)


def test_reset_with_same_seed_reproduces_the_sequence():
    reset_rng(123)
    first = get_rng().random(5).tolist()
    reset_rng(123)
    second = get_rng().random(5).tolist()
    assert first == second


def test_reset_swaps_the_shared_instance():
    original = reset_rng(1)
    replacement = reset_rng(2)
    assert get_rng() is replacement
    assert original is not replacement


def test_different_seeds_produce_different_sequences():
    reset_rng(1)
    a = get_rng().random(5).tolist()
    reset_rng(2)
    b = get_rng().random(5).tolist()
    assert a != b

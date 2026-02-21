"""
Random Number Generator utilities.

Provides a shared RNG instance for reproducible random operations
across all diagnostic services.
"""

import numpy as np

# Shared RNG with fixed seed for reproducibility
_DEFAULT_SEED = 42
_rng: np.random.Generator = np.random.default_rng(_DEFAULT_SEED)


def get_rng() -> np.random.Generator:
    """Get the shared random number generator instance."""
    return _rng


def reset_rng(seed: int = _DEFAULT_SEED) -> np.random.Generator:
    """Reset the RNG with a new seed. Useful for testing."""
    global _rng
    _rng = np.random.default_rng(seed)
    return _rng

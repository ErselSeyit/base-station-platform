"""
Isolation Forest anomaly scorer.

A small, dependency-light Isolation Forest (Liu et al.) over numpy arrays,
extracted from anomaly_detection.py so the unsupervised scorer can be tested and
reused apart from the metric-ingestion detector. Randomness flows through the
shared seeded RNG so results stay reproducible.
"""

import random
from typing import Dict, List

import numpy as np

from .utils.rng import get_rng

_rng = get_rng()


class IsolationTree:
    """
    Single tree in the Isolation Forest.

    Recursively partitions data by random splits until points are isolated.
    Anomalies are isolated quickly (short path length).
    """

    def __init__(self, height_limit: int):
        self.height_limit = height_limit
        self.root = None
        self.n_features = 0

    def fit(self, X: np.ndarray) -> None:
        """Build the isolation tree."""
        self.n_features = X.shape[1]
        self.root = self._build_tree(X, 0)

    def _build_tree(self, X: np.ndarray, height: int) -> Dict:
        """Recursively build tree nodes."""
        n_samples = X.shape[0]

        # Terminal conditions
        if height >= self.height_limit or n_samples <= 1:
            return {"type": "leaf", "size": n_samples}

        # Random feature and split value
        feature_idx = random.randint(0, self.n_features - 1)
        feature_values = X[:, feature_idx]
        min_val, max_val = feature_values.min(), feature_values.max()

        if min_val == max_val:
            return {"type": "leaf", "size": n_samples}

        split_value = random.uniform(min_val, max_val)

        # Split data
        left_mask = feature_values < split_value
        right_mask = ~left_mask

        return {
            "type": "internal",
            "feature": feature_idx,
            "split": split_value,
            "left": self._build_tree(X[left_mask], height + 1),
            "right": self._build_tree(X[right_mask], height + 1),
        }

    def path_length(self, x: np.ndarray) -> float:
        """Compute path length for a single point."""
        if self.root is None:
            raise ValueError("Tree not fitted. Call fit() first.")
        return self._traverse(x, self.root, 0)

    def _traverse(self, x: np.ndarray, node: Dict, height: int) -> float:
        """Traverse tree to find path length."""
        if node["type"] == "leaf":
            # Average path length adjustment for external nodes
            n = node["size"]
            if n <= 1:
                return height
            else:
                # Expected path length in a BST
                return height + self._c(n)

        if x[node["feature"]] < node["split"]:
            return self._traverse(x, node["left"], height + 1)
        else:
            return self._traverse(x, node["right"], height + 1)

    @staticmethod
    def _c(n: int) -> float:
        """Average path length of unsuccessful search in BST."""
        if n <= 1:
            return 0
        return 2 * (np.log(n - 1) + 0.5772156649) - 2 * (n - 1) / n


class IsolationForest:
    """
    Isolation Forest for anomaly detection.

    Ensemble of isolation trees that scores points based on
    how quickly they become isolated.
    """

    def __init__(
        self,
        n_trees: int = 100,
        sample_size: int = 256,
        contamination: float = 0.1,
    ):
        self.n_trees = n_trees
        self.sample_size = sample_size
        self.contamination = contamination
        self.trees: List[IsolationTree] = []
        self.threshold = 0.5
        self._fitted = False

    def fit(self, X: np.ndarray) -> None:
        """Fit the isolation forest."""
        n_samples = X.shape[0]
        height_limit = int(np.ceil(np.log2(max(self.sample_size, 2))))

        self.trees = []
        for _ in range(self.n_trees):
            tree = IsolationTree(height_limit)

            # Subsample
            if n_samples > self.sample_size:
                indices = _rng.choice(n_samples, self.sample_size, replace=False)
                tree.fit(X[indices])
            else:
                tree.fit(X)

            self.trees.append(tree)

        # Set threshold based on contamination
        scores = self.score_samples(X)
        self.threshold = np.percentile(scores, 100 * (1 - self.contamination))
        self._fitted = True

    def score_samples(self, X: np.ndarray) -> np.ndarray:
        """
        Compute anomaly scores for samples.

        Returns:
            Array of scores where higher = more anomalous
        """
        if not self.trees:
            return np.zeros(X.shape[0])

        # Average path length across all trees
        avg_path_lengths = np.zeros(X.shape[0])
        for tree in self.trees:
            for i in range(X.shape[0]):
                avg_path_lengths[i] += tree.path_length(X[i])
        avg_path_lengths /= len(self.trees)

        # Normalize to anomaly score (0-1)
        c = IsolationTree._c(self.sample_size)
        scores = 2 ** (-avg_path_lengths / c)

        return scores

    def predict(self, X: np.ndarray) -> np.ndarray:
        """
        Predict anomalies.

        Returns:
            Array of -1 (anomaly) or 1 (normal)
        """
        scores = self.score_samples(X)
        return np.where(scores > self.threshold, -1, 1)

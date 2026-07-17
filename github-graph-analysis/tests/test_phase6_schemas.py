import unittest

from pydantic import ValidationError

from app.schemas.failure_analysis import FailureInput, LocalizationConfiguration
from app.schemas.similarity import DEFAULT_SIMILARITY_PROFILES, FeatureProfile


class Phase6SchemaTest(unittest.TestCase):
    def test_default_similarity_profiles_are_valid(self) -> None:
        self.assertEqual(0.40, DEFAULT_SIMILARITY_PROFILES.function.weights["calledNodes"])
        self.assertEqual(0.45, DEFAULT_SIMILARITY_PROFILES.failurePath.weights["pathNodes"])

    def test_feature_profile_requires_a_positive_weight(self) -> None:
        with self.assertRaises(ValidationError):
            FeatureProfile(name="invalid", weights={"calledNodes": 0.0})

    def test_failure_input_requires_evidence(self) -> None:
        with self.assertRaises(ValidationError):
            FailureInput(repositoryId="repo-example")

    def test_failure_input_accepts_a_failing_node(self) -> None:
        failure = FailureInput(repositoryId="repo-example", failingNodeId="function:authenticate")

        self.assertEqual("function:authenticate", failure.failingNodeId)

    def test_localization_configuration_requires_positive_weight(self) -> None:
        with self.assertRaises(ValidationError):
            LocalizationConfiguration(
                pathEvidenceWeight=0.0,
                stackEvidenceWeight=0.0,
                historyEvidenceWeight=0.0,
                structuralEvidenceWeight=0.0,
                criticalityEvidenceWeight=0.0,
            )


if __name__ == "__main__":
    unittest.main()

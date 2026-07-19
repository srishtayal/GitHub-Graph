import unittest

from app.services.explanations.evidence_selector import EvidenceSelector
from app.services.explanations.prompt_builder import PromptBuilder
from app.services.explanations.query_router import QueryRouter
from tests.fixtures.phase7_explanations import dependency_request


class PromptBuilderTest(unittest.TestCase):
    def test_prompt_has_grounding_instructions_and_limited_evidence(self) -> None:
        request = dependency_request()
        intent = QueryRouter().route(request.query)
        prompt = PromptBuilder().build(request, intent, EvidenceSelector().select(request, intent))

        self.assertIn("Answer only from the EVIDENCE JSON", prompt)
        self.assertIn("analytics:dependency-trace", prompt)
        self.assertNotIn("edge:07", prompt)
        self.assertIn("ALLOWED_NODE_IDS", prompt)


if __name__ == "__main__":
    unittest.main()

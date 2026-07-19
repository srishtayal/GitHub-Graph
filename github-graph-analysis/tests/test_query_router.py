import unittest

from app.services.explanations.query_router import QueryRouter


class QueryRouterTest(unittest.TestCase):
    def setUp(self) -> None:
        self.router = QueryRouter()

    def test_routes_phase7_question_categories(self) -> None:
        cases = {
            "Explain login flow": "dependency_flow",
            "What breaks if dbConnection fails?": "impact_analysis",
            "Why is this error happening?": "bug_explanation",
            "Show the most critical functions": "criticality",
            "Why are these two files similar?": "similarity",
            "Is there a dependency cycle?": "cycle_or_order",
        }
        for query, expected in cases.items():
            with self.subTest(query=query):
                self.assertEqual(expected, self.router.route(query))

    def test_unknown_question_is_not_sent_to_the_model(self) -> None:
        self.assertEqual("unknown_or_insufficient", self.router.route("Tell me something interesting"))


if __name__ == "__main__":
    unittest.main()

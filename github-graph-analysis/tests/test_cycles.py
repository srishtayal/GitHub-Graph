import unittest

from app.analytics.cycles import detect_cycles
from tests.fixtures.graph_fixtures import call_cycle_graph, primary_graph, self_cycle_graph


class CycleDetectionTest(unittest.TestCase):
    def test_detects_import_use_cycle_as_closed_path(self) -> None:
        result = detect_cycles(primary_graph())

        self.assertTrue(result.hasCycles)
        self.assertEqual(1, len(result.cycles))
        self.assertEqual(
            ["file:cycle-a", "file:cycle-b", "file:cycle-a"],
            result.cycles[0].nodeIds,
        )
        self.assertEqual(["USES", "USES"], result.cycles[0].edgeTypes)

    def test_call_cycles_are_opt_in_and_self_cycles_are_detected(self) -> None:
        default_result = detect_cycles(call_cycle_graph())
        call_result = detect_cycles(call_cycle_graph(), edge_types={"CALLS"})
        self_cycle_result = detect_cycles(self_cycle_graph())

        self.assertFalse(default_result.hasCycles)
        self.assertEqual(
            ["function:a", "function:b", "function:a"],
            call_result.cycles[0].nodeIds,
        )
        self.assertEqual(["file:self", "file:self"], self_cycle_result.cycles[0].nodeIds)


if __name__ == "__main__":
    unittest.main()

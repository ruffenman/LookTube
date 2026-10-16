import importlib.util
import contextlib
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import subprocess

spec = importlib.util.spec_from_file_location("local_ci", Path(__file__).resolve().parents[1] / "local_ci.py")
ci = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ci)


class LocalCiTest(unittest.TestCase):
    def setUp(self):
        quiet = contextlib.redirect_stdout(io.StringIO())
        quiet.__enter__()
        self.addCleanup(quiet.__exit__, None, None, None)

    def test_native_requires_full_success_even_with_adb_exit_zero(self):
        for output in ["", "OK (0 tests)\nINSTRUMENTATION_CODE: -1", "OK (2 tests)\nINSTRUMENTATION_CODE: -1",
                       "OK (3 tests)", "FAILURES!!!\nINSTRUMENTATION_CODE: -1",
                       "Process crashed\nOK (3 tests)\nINSTRUMENTATION_CODE: -1"]:
            with self.subTest(output=output), self.assertRaises(ci.StepFailure):
                ci.validate_instrumentation(output)
        ci.validate_instrumentation("\nOK (3 tests)\n\nINSTRUMENTATION_CODE: -1\n")

    def test_offline_or_unauthorized_device_never_passes_preflight(self):
        for output in ["offline", "unauthorized", "", "device\noffline"]:
            with self.subTest(output=output), self.assertRaises(ci.StepFailure):
                ci.require_device(output)
        ci.require_device("device\n")

    def test_failed_command_is_saved_and_raised(self):
        with tempfile.TemporaryDirectory() as temporary:
            run = ci.Run(Path(temporary) / "run", "fast", "test")
            with patch.object(ci.subprocess, "run", return_value=subprocess.CompletedProcess([], 7)):
                with self.assertRaises(ci.StepFailure):
                    run.step("bad", ["fake-command"])
            self.assertEqual("failed", run.summary["steps"][0]["status"])
            self.assertIn('"exit_code": 7', (run.folder / "summary.json").read_text())

    def test_zero_exit_with_bad_native_output_is_saved_as_failure(self):
        with tempfile.TemporaryDirectory() as temporary:
            run = ci.Run(Path(temporary) / "run", "device", "test")
            with patch.object(ci.subprocess, "run", return_value=subprocess.CompletedProcess([], 0)):
                with self.assertRaises(ci.StepFailure):
                    run.step("native", ["adb"], ci.validate_instrumentation)
            self.assertEqual("failed", run.summary["steps"][0]["status"])

    def test_unexpected_validation_error_cannot_leave_a_running_step(self):
        def broken_validator(output):
            raise ValueError("Unexpected parser failure")
        with tempfile.TemporaryDirectory() as temporary:
            run = ci.Run(Path(temporary) / "run", "device", "test")
            with patch.object(ci.subprocess, "run", return_value=subprocess.CompletedProcess([], 0)):
                with self.assertRaises(ci.StepFailure):
                    run.step("parser", ["adb"], broken_validator)
            self.assertEqual("failed", run.summary["steps"][0]["status"])

    def test_timeout_is_saved_as_failure(self):
        with tempfile.TemporaryDirectory() as temporary:
            run = ci.Run(Path(temporary) / "run", "device", "test")
            with patch.object(ci.subprocess, "run", side_effect=subprocess.TimeoutExpired(["adb"], 1)):
                with self.assertRaises(ci.StepFailure):
                    run.step("timeout", ["adb"], timeout=1)
            self.assertEqual("failed", run.summary["steps"][0]["status"])

    def test_device_must_match_highspec_requirements(self):
        with self.assertRaises(ci.StepFailure): ci.require_api("34")
        with self.assertRaises(ci.StepFailure): ci.require_abi("x86_64")
        ci.require_api("37\n")
        ci.require_abi("arm64-v8a,armeabi-v7a\n")


if __name__ == "__main__":
    unittest.main()

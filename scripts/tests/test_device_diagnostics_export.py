import importlib.util
import json
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("diagnostic_export", Path(__file__).parents[1] / "export_device_diagnostics.py")
export = importlib.util.module_from_spec(spec)
spec.loader.exec_module(export)

class DeviceDiagnosticExportTests(unittest.TestCase):
    def data(self):
        return {"schema": 1, "phase": "deadline", "scope": "instrumentation metadata only", "threads": [{"id": 1, "state": "WAITING", "frames_truncated": False, "frames": [{"class": "example.Test", "method": "execute", "file": "Test.kt", "line": 10}]}]}
    def test_numeric_run_is_scoped_to_private_diagnostic_namespace(self):
        self.assertEqual("cache/device-test-diagnostics/run-123", export.run_path("run-123"))
    def test_path_traversal_and_shell_words_are_refused(self):
        for value in ["../files", "run-1/../../files", "run-1;id", "run-$(id)", "run-1\n"]:
            with self.subTest(value=value), self.assertRaises(ValueError): export.run_path(value)
    def test_only_declared_files_inside_that_run_are_exportable(self):
        prefix=export.run_path("run-123")
        self.assertEqual("snapshot-1-2-deadline.json", export.metadata_path(prefix+"/snapshot-1-2-deadline.json", prefix))
        for name in [prefix+"/../credentials.json", prefix+"/unknown.json", prefix+"/case-1-start.json.part", "cache/elsewhere/run-start.json"]:
            with self.subTest(name=name), self.assertRaises(ValueError): export.metadata_path(name,prefix)
    def test_value_free_stack_metadata_is_accepted(self):
        data=self.data();self.assertEqual(data,export.decode_metadata(json.dumps(data).encode()))
    def test_thread_names_and_locals_cannot_be_exported(self):
        for target,key,value in [("thread","name","https://private.example/token"),("frame","locals",{"token":"private"}),("top","args",{"password":"private"})]:
            data=self.data();dest=data if target=="top" else data["threads"][0] if target=="thread" else data["threads"][0]["frames"][0];dest[key]=value
            with self.subTest(target=target),self.assertRaises(ValueError):export.decode_metadata(json.dumps(data).encode())
    def test_absolute_build_paths_and_excessive_metadata_are_refused(self):
        data=self.data();data["threads"][0]["frames"][0]["file"]="/private/build/Test.kt"
        with self.assertRaises(ValueError):export.decode_metadata(json.dumps(data).encode())
        with self.assertRaises(ValueError):export.decode_metadata(b"x"*(export.MAX_FILE+1))
    def test_unknown_schema_and_too_many_threads_are_refused(self):
        data=self.data();data["schema"]=2
        with self.assertRaises(ValueError):export.decode_metadata(json.dumps(data).encode())
        data=self.data();data["threads"]*=33
        with self.assertRaises(ValueError):export.decode_metadata(json.dumps(data).encode())
    def test_package_paths_cannot_inject_a_remote_shell_or_select_split_apks(self):
        self.assertEqual("/data/app/~~abc/pkg-xyz==/base.apk",export.installed_path("package:/data/app/~~abc/pkg-xyz==/base.apk\n"))
        for value in ["package:/data/app/pkg/base.apk;id", "package:/data/app/pkg/base.apk\npackage:/data/app/pkg/split.apk", "package:/sdcard/base.apk", "package:/data/app/../private/base.apk"]:
            with self.subTest(value=value),self.assertRaises(ValueError):export.installed_path(value)

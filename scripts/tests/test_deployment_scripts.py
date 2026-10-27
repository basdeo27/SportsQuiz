"""Validate deployment configuration without calling AWS or changing live infrastructure."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class ApiProxyTests(unittest.TestCase):
    def run_proxy(self, backend="https://backend.example.test", existing=None):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            current = existing or {"Enabled": True, "Comment": "Keep this", "Origins": {"Quantity": 1, "Items": [{"Id": "static", "DomainName": "bucket.example", "S3OriginConfig": {"OriginAccessIdentity": ""}, "OriginAccessControlId": "keep-oac"}]},
                                   "DefaultCacheBehavior": {"TargetOriginId": "static"},
                                   "CacheBehaviors": {"Quantity": 1, "Items": [{"PathPattern": "/images/*", "TargetOriginId": "static"}]}}
            (folder / "current.json").write_text(json.dumps({"ETag": "version", "DistributionConfig": current}))
            aws = folder / "aws"
            aws.write_text('''#!/usr/bin/env python3
import json, os, sys
from pathlib import Path
root = Path(os.environ["MOCK_ROOT"])
args = sys.argv[1:]
if args[:2] == ["cloudfront", "get-distribution-config"]: print((root / "current.json").read_text())
elif args[:2] == ["cloudfront", "list-cache-policies"]: print("disabled-policy")
elif args[:2] == ["cloudfront", "list-origin-request-policies"]: print("forward-policy")
elif args[:2] == ["cloudfront", "update-distribution"]:
    filename = args[args.index("--distribution-config") + 1].removeprefix("file://")
    (root / "captured.json").write_text(Path(filename).read_text())
    print("InProgress")
elif args[:2] == ["cloudfront", "wait"]: pass
elif args[:2] == ["cloudfront", "get-distribution"]: print("cdn.example.test")
else: raise SystemExit("Unexpected AWS call")
''')
            aws.chmod(0o755)
            curl = folder / "curl"
            curl.write_text("#!/bin/sh\nexit 0\n")
            curl.chmod(0o755)
            environment = {**os.environ, "PATH": f"{folder}:{os.environ['PATH']}", "MOCK_ROOT": str(folder),
                           "BACKEND_URL": backend, "CLOUDFRONT_DISTRIBUTION_ID": "test-distribution", "CLOUDFRONT_CACHE_POLICY_ID": "disabled-policy", "CLOUDFRONT_ORIGIN_POLICY_ID": "forward-policy"}
            result = subprocess.run(["bash", str(ROOT / "scripts/configure_api_proxy.sh")], env=environment, capture_output=True, text=True)
            capture = folder / "captured.json"
            return result, json.loads(capture.read_text()) if capture.exists() else None

    def test_preserves_static_configuration_and_routes_uncached_authenticated_api(self):
        result, config = self.run_proxy()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(config["DefaultCacheBehavior"]["TargetOriginId"], "static")
        self.assertEqual(config["Comment"], "Keep this")
        self.assertEqual(config["Origins"]["Quantity"], 2)
        static = next(item for item in config["Origins"]["Items"] if item["Id"] == "static")
        self.assertEqual(static["S3OriginConfig"], {"OriginAccessIdentity": "", "OriginReadTimeout": 30})
        self.assertEqual(static["OriginAccessControlId"], "keep-oac")
        origin = next(item for item in config["Origins"]["Items"] if item["Id"] == "SportsQuizApi")
        self.assertEqual(origin["CustomOriginConfig"]["OriginReadTimeout"], 30)
        self.assertEqual(origin["CustomOriginConfig"]["OriginKeepaliveTimeout"], 5)
        self.assertEqual(origin["CustomOriginConfig"]["OriginProtocolPolicy"], "https-only")
        api = config["CacheBehaviors"]["Items"][0]
        self.assertEqual(api["PathPattern"], "/v0/*")
        self.assertEqual(api["CachePolicyId"], "disabled-policy")
        self.assertEqual(api["OriginRequestPolicyId"], "forward-policy")
        self.assertIn("POST", api["AllowedMethods"]["Items"])
        self.assertEqual(config["CacheBehaviors"]["Items"][1]["PathPattern"], "/assets/daily/*")
        self.assertEqual(config["CacheBehaviors"]["Items"][2]["PathPattern"], "/images/*")
        self.assertIs(config["DefaultCacheBehavior"]["SmoothStreaming"], False)
        for behavior in config["CacheBehaviors"]["Items"]:
            self.assertIs(behavior["SmoothStreaming"], False)

    def test_preserves_existing_smooth_streaming_settings(self):
        _, first = self.run_proxy()
        first["DefaultCacheBehavior"]["SmoothStreaming"] = True
        first["CacheBehaviors"]["Items"][2]["SmoothStreaming"] = True
        result, updated = self.run_proxy(existing=first)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIs(updated["DefaultCacheBehavior"]["SmoothStreaming"], True)
        self.assertIs(updated["CacheBehaviors"]["Items"][2]["SmoothStreaming"], True)
        self.assertIs(updated["CacheBehaviors"]["Items"][0]["SmoothStreaming"], False)

    def test_configuration_is_idempotent(self):
        _, first = self.run_proxy()
        result, second = self.run_proxy(existing=first)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(first, second)

    def test_preserves_explicit_timeouts_on_existing_origins(self):
        _, first = self.run_proxy()
        first["Origins"]["Items"][0]["S3OriginConfig"]["OriginReadTimeout"] = 45
        first["Origins"]["Items"].append({
            "Id": "other-api", "DomainName": "other.example.test",
            "CustomOriginConfig": {"OriginReadTimeout": 60, "OriginKeepaliveTimeout": 10}
        })
        first["Origins"]["Quantity"] += 1
        result, updated = self.run_proxy(existing=first)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(updated["Origins"]["Items"][0], first["Origins"]["Items"][0])
        other = next(item for item in updated["Origins"]["Items"] if item["Id"] == "other-api")
        self.assertEqual(other["CustomOriginConfig"], {"OriginReadTimeout": 60, "OriginKeepaliveTimeout": 10})

    def test_rejects_insecure_or_credential_bearing_backend_without_update(self):
        for backend in ["http://backend.example.test", "https://user:password@backend.example.test", "https://backend.example.test/v0"]:
            result, captured = self.run_proxy(backend=backend)
            self.assertNotEqual(result.returncode, 0)
            self.assertIsNone(captured)


if __name__ == "__main__":
    unittest.main()

#!/usr/bin/env python3
"""One-time/manual AWS setup. Never invoked by a normal application release."""
import hashlib
import json
import os
import subprocess
import time
from pathlib import Path

from release import AwsError, ReleaseError, aws, current_service, https_root, required


def configure():
    required("CLOUDFRONT_DISTRIBUTION_ID")
    https_root(required("BACKEND_URL"))
    name = required("ECR_REPOSITORY")
    try:
        repo = aws("ecr", "describe-repositories", "--repository-names", name)["repositories"][0]
        if repo.get("imageTagMutability") != "IMMUTABLE":
            aws("ecr", "put-image-tag-mutability", "--repository-name", name, "--image-tag-mutability", "IMMUTABLE")
    except AwsError as error:
        if error.code != "RepositoryNotFoundException":
            raise
        aws("ecr", "create-repository", "--repository-name", name, "--image-tag-mutability", "IMMUTABLE",
            "--image-scanning-configuration", "scanOnPush=true")
    aws("lightsail", "update-container-service", "--service-name", required("SERVICE_NAME"),
        "--private-registry-access", json.dumps({"ecrImagePullerRole": {"isActive": True}}))
    principal = None
    for _ in range(30):
        role = current_service().get("privateRegistryAccess", {}).get("ecrImagePullerRole", {})
        principal = role.get("principalArn")
        if role.get("isActive") and principal:
            break
        time.sleep(2)
    else:
        raise ReleaseError("Lightsail image-puller role did not become ready.")
    try:
        response = aws("ecr", "get-repository-policy", "--repository-name", name)
        policy = json.loads(response["policyText"])
    except AwsError as error:
        if error.code != "RepositoryPolicyNotFoundException":
            raise
        policy = {"Version": "2012-10-17", "Statement": []}
    statement_id = "SportsQuizLightsailPull" + hashlib.sha256(required("SERVICE_NAME").encode()).hexdigest()[:8]
    statements = policy.get("Statement", [])
    if isinstance(statements, dict):
        statements = [statements]
    policy["Statement"] = [entry for entry in statements if entry.get("Sid") != statement_id] + [{
        "Sid": statement_id, "Effect": "Allow", "Principal": {"AWS": principal},
        "Action": ["ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer", "ecr:BatchCheckLayerAvailability"],
    }]
    aws("ecr", "set-repository-policy", "--repository-name", name, "--policy-text", json.dumps(policy))
    subprocess.run(["bash", str(Path(__file__).with_name("configure_api_proxy.sh"))], check=True, env=os.environ.copy())
    print("ECR, Lightsail image access and first-party API routing are configured.")


if __name__ == "__main__":
    try:
        configure()
    except (ReleaseError, ValueError, KeyError, subprocess.CalledProcessError) as error:
        raise SystemExit(f"Infrastructure setup failed: {error}")

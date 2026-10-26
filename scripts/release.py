#!/usr/bin/env python3
"""Build/release helpers. No AWS calls happen on import or during prepare.

The deploy and rollback commands are called only by the production Actions jobs.
Runtime secrets are never written into a release manifest or printed to stdout.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import time
from datetime import datetime, timezone
from urllib.parse import urlparse
from urllib.request import Request, urlopen


class ReleaseError(RuntimeError):
    pass


class AwsError(ReleaseError):
    def __init__(self, operation, code):
        self.code = code
        super().__init__(f"AWS {operation} failed ({code}). Check the deployment role and AWS service logs.")


def required(name):
    value = os.environ.get(name, "").strip()
    if not value:
        raise ReleaseError(f"Set {name} before deploying.")
    return value


def commit_sha(value):
    if not isinstance(value, str) or not re.fullmatch(r"[0-9a-f]{40}", value):
        raise ReleaseError("Release commit must be a full 40-character lowercase commit SHA.")
    return value


def https_root(value):
    parsed = urlparse(value)
    if (parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password
            or parsed.path not in ("", "/") or parsed.query or parsed.fragment or parsed.port not in (None, 443)):
        raise ReleaseError("SITE_URL must be an HTTPS root URL without credentials or a path.")
    return value.rstrip("/")


def checksum(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def write_json(path, value):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(json.dumps(value, indent=2) + "\n")


def aws(*args, json_result=True):
    command = ["aws", "--region", required("AWS_REGION"), *map(str, args)]
    if json_result:
        command += ["--output", "json"]
    result = subprocess.run(command, capture_output=True, text=True, check=False)
    if result.returncode:
        # AWS responses can include deployment environment values. Never echo them.
        match = re.search(r"An error occurred \(([^)]+)\)", result.stderr)
        raise AwsError(" ".join(map(str, args[:2])), match.group(1) if match else f"exit {result.returncode}")
    if not json_result:
        return None
    try:
        return json.loads(result.stdout)
    except ValueError as error:
        raise ReleaseError(f"AWS {' '.join(map(str, args[:2]))} returned invalid JSON.") from error


def prepare(directory):
    commit = commit_sha(required("RELEASE_COMMIT"))
    jars = [path for path in Path("build/libs").glob("*.jar") if not path.name.endswith("-plain.jar")]
    if len(jars) != 1 or not Path("frontend/dist/index.html").is_file():
        raise ReleaseError("Build exactly one bootJar and the frontend before packaging a release.")
    directory.mkdir(parents=True, exist_ok=True)
    (directory / "backend").mkdir(exist_ok=True)
    shutil.copyfile(jars[0], directory / "backend/app.jar")
    write_json("frontend/dist/release.json", {"commit": commit})
    archive = directory / "frontend.tar.gz"
    with tarfile.open(archive, "w:gz") as bundle:
        for path in sorted(Path("frontend/dist").rglob("*")):
            if path.is_symlink():
                raise ReleaseError("Frontend release must not contain symlinks.")
            if path.is_file():
                bundle.add(path, arcname=str(path.relative_to("frontend/dist")), recursive=False)
    write_json(directory / "manifest.json", {
        "schema": 1, "commit": commit,
        "packagedAt": datetime.now(timezone.utc).isoformat(),
        "frontendSha256": checksum(archive),
    })
    print(f"Packaged tested backend and frontend for {commit}.")


def repository():
    name = required("ECR_REPOSITORY")
    response = aws("ecr", "describe-repositories", "--repository-names", name)["repositories"][0]
    if response.get("imageTagMutability") != "IMMUTABLE":
        raise ReleaseError("Configure the ECR repository with immutable tags before deploying.")
    return response["repositoryUri"]


def image_details(commit):
    try:
        return aws("ecr", "describe-images", "--repository-name", required("ECR_REPOSITORY"),
                   "--image-ids", f"imageTag={commit}")["imageDetails"][0]
    except AwsError as error:
        if error.code == "ImageNotFoundException":
            return None
        raise


def image_output():
    commit = commit_sha(required("RELEASE_COMMIT"))
    uri = f"{repository()}:{commit}"
    exists = image_details(commit) is not None
    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with Path(output).open("a") as stream:
            stream.write(f"uri={uri}\nexists={str(exists).lower()}\n")
    print(f"{'Reusing' if exists else 'Publishing'} immutable image {uri}.")


def load_manifest(path, expected_commit=None):
    value = json.loads(Path(path).read_text())
    if value.get("schema") != 1:
        raise ReleaseError("Unsupported release manifest schema.")
    commit = commit_sha(value.get("commit", ""))
    if expected_commit is not None and commit != expected_commit:
        raise ReleaseError("Release manifest does not match the requested commit.")
    if not re.fullmatch(r"[0-9a-f]{64}", value.get("frontendSha256", "")):
        raise ReleaseError("Release manifest has no valid frontend checksum.")
    return value


def extract_frontend(archive, destination, manifest):
    if checksum(archive) != manifest["frontendSha256"]:
        raise ReleaseError("Frontend checksum does not match its release manifest.")
    destination.mkdir(parents=True, exist_ok=True)
    with tarfile.open(archive) as bundle:
        for member in bundle.getmembers():
            target = (destination / member.name).resolve()
            if not target.is_relative_to(destination.resolve()) or not (member.isfile() or member.isdir()):
                raise ReleaseError("Frontend archive contains an unsafe path or link.")
            if member.isdir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with bundle.extractfile(member) as source, target.open("wb") as output:
                    shutil.copyfileobj(source, output)
    if not (destination / "index.html").is_file():
        raise ReleaseError("Frontend release is missing index.html.")
    if json.loads((destination / "release.json").read_text()).get("commit") != manifest["commit"]:
        raise ReleaseError("Frontend release marker does not match its manifest.")


def remote_json(key):
    try:
        aws("s3api", "head-object", "--bucket", required("S3_BUCKET"), "--key", key)
    except AwsError as error:
        if error.code in ("404", "NotFound", "NoSuchKey"):
            return None
        raise
    with tempfile.TemporaryDirectory() as temporary:
        path = Path(temporary) / "response.json"
        aws("s3", "cp", f"s3://{required('S3_BUCKET')}/{key}", path, "--only-show-errors", json_result=False)
        return json.loads(path.read_text())


def download_release(commit, directory):
    commit_sha(commit)
    directory.mkdir(parents=True, exist_ok=True)
    root = f"s3://{required('S3_BUCKET')}/_releases/{commit}"
    for filename in ("manifest.json", "frontend.tar.gz"):
        aws("s3", "cp", f"{root}/{filename}", directory / filename, "--only-show-errors", json_result=False)
    manifest = load_manifest(directory / "manifest.json", commit)
    extract_frontend(directory / "frontend.tar.gz", directory / "frontend", manifest)
    return manifest


def validate_image(manifest):
    expected = f"{repository()}:{manifest['commit']}"
    details = image_details(manifest["commit"])
    if manifest.get("image") != expected or details is None or manifest.get("imageDigest") != details.get("imageDigest"):
        raise ReleaseError("Published image no longer matches the release manifest.")


def current_service():
    services = aws("lightsail", "get-container-services", "--service-name", required("SERVICE_NAME")).get("containerServices", [])
    if len(services) != 1:
        raise ReleaseError("Could not identify the configured Lightsail service.")
    return services[0]


def runtime_environment(previous):
    environment = dict(previous)
    app_key = os.environ.get("APP_AWS_ACCESS_KEY_ID", "")
    app_secret = os.environ.get("APP_AWS_SECRET_ACCESS_KEY", "")
    if bool(app_key) != bool(app_secret):
        raise ReleaseError("Provide both APP_AWS_ACCESS_KEY_ID and APP_AWS_SECRET_ACCESS_KEY.")
    if app_key:
        environment.update(AWS_ACCESS_KEY_ID=app_key, AWS_SECRET_ACCESS_KEY=app_secret)
        for obsolete in ("AWS_SESSION_TOKEN", "AWS_DYNAMO_ACCESS_KEY_ID", "AWS_DYNAMO_SECRET_ACCESS_KEY"):
            environment.pop(obsolete, None)
    if not environment.get("AWS_ACCESS_KEY_ID") or not environment.get("AWS_SECRET_ACCESS_KEY"):
        raise ReleaseError("Configure application AWS credentials in Lightsail or the APP_AWS_* GitHub secrets. CI role credentials are never copied into the application.")
    environment.update(SPRING_PROFILES_ACTIVE="prod", QUIZ_STORAGE="dynamo", AWS_REGION=required("AWS_REGION"),
                       FRONTEND_URL=https_root(required("SITE_URL")))
    for name in ("MISTRAL_API_KEY", "MONITORING_API_KEY", "MAIL_FROM", "DAILY_LOGIN_REQUIRED"):
        if os.environ.get(name):
            environment[name] = os.environ[name]
    return environment


def backend_request(service, manifest):
    previous = (service.get("currentDeployment") or {})
    containers = previous.get("containers", {}).copy()
    endpoint = previous.get("publicEndpoint", {}).copy()
    name = endpoint.get("containerName", "app")
    app = dict(containers.get(name, {}))
    app["image"] = manifest["image"]
    app["environment"] = runtime_environment(app.get("environment", {}))
    app["ports"] = {**app.get("ports", {}), "8080": "HTTP"}
    containers[name] = app
    endpoint.update(containerName=name, containerPort=8080)
    endpoint["healthCheck"] = {"path": "/ping", "intervalSeconds": 10, "timeoutSeconds": 5,
                               "healthyThreshold": 2, "unhealthyThreshold": 3}
    return {"serviceName": required("SERVICE_NAME"), "containers": containers, "publicEndpoint": endpoint}


def http_json(url):
    try:
        with urlopen(Request(url, headers={"User-Agent": "SportsQuiz-Release-Check", "Cache-Control": "no-cache"}), timeout=20) as response:
            return json.load(response)
    except Exception as error:
        raise ReleaseError(f"Smoke check failed for {url}.") from error


def wait_backend(version):
    for _ in range(60):
        service = current_service()
        current = (service.get("currentDeployment") or {})
        pending = service.get("nextDeployment") or {}
        if current.get("version") == version and current.get("state") == "ACTIVE":
            root = https_root(service["url"])
            if http_json(f"{root}/actuator/health").get("status") != "UP":
                raise ReleaseError("Backend health check did not report UP.")
            if not http_json(f"{root}/v0/quiz/leagues"):
                raise ReleaseError("Backend API smoke check returned no leagues.")
            return
        if ((pending.get("version") == version and pending.get("state") == "FAILED")
                or (current.get("version") == version and current.get("state") == "FAILED")):
            raise ReleaseError(f"Lightsail deployment {version} failed to start.")
        if current.get("version", 0) > version:
            raise ReleaseError("Another deployment replaced this release during verification.")
        time.sleep(10)
    raise ReleaseError(f"Timed out waiting for Lightsail deployment {version}.")


def activate_backend(manifest):
    request = backend_request(current_service(), manifest)
    with tempfile.TemporaryDirectory() as temporary:
        path = Path(temporary) / "deployment.json"
        write_json(path, request)
        path.chmod(0o600)
        response = aws("lightsail", "create-container-service-deployment", "--cli-input-json", f"file://{path}")
    service = response["containerService"]
    version = (service.get("nextDeployment") or (service.get("currentDeployment") or {})).get("version")
    if not isinstance(version, int):
        raise ReleaseError("Lightsail returned no deployment version.")
    print(f"Waiting for backend deployment {version}.")
    wait_backend(version)
    return version


def publish_daily_media(directory, manifest):
    # New backend responses can reference these URLs immediately after activation.
    frontend = directory / "frontend"
    if not (frontend / "index.html").is_file():
        extract_frontend(directory / "frontend.tar.gz", frontend, manifest)
    media = frontend / "media/daily"
    if media.is_dir():
        aws("s3", "sync", media, f"s3://{required('S3_BUCKET')}/media/daily",
            "--cache-control", "public,max-age=31536000,immutable", "--only-show-errors", json_result=False)


def publish_frontend(directory, manifest):
    frontend = directory / "frontend"
    if not (frontend / "index.html").is_file():
        extract_frontend(directory / "frontend.tar.gz", frontend, manifest)
    destination = f"s3://{required('S3_BUCKET')}"
    # Upload assets first. Keep previous hashed assets for existing browsers and rollback.
    aws("s3", "sync", frontend, destination, "--exclude", "index.html", "--exclude", "release.json",
        "--exclude", "_releases/*", "--cache-control", "public,max-age=31536000,immutable", "--only-show-errors", json_result=False)
    for name in ("index.html", "release.json"):
        aws("s3", "cp", frontend / name, f"{destination}/{name}", "--cache-control", "no-cache,no-store,must-revalidate",
            "--content-type", "text/html" if name.endswith("html") else "application/json", "--only-show-errors", json_result=False)
    invalidation = aws("cloudfront", "create-invalidation", "--distribution-id", required("CLOUDFRONT_DISTRIBUTION_ID"),
                       "--paths", "/", "/index.html", "/release.json")
    aws("cloudfront", "wait", "invalidation-completed", "--distribution-id", required("CLOUDFRONT_DISTRIBUTION_ID"),
        "--id", invalidation["Invalidation"]["Id"], json_result=False)
    root = https_root(required("SITE_URL"))
    if http_json(f"{root}/release.json").get("commit") != manifest["commit"]:
        raise ReleaseError("The frontend is not serving the expected release.")
    if not http_json(f"{root}/v0/quiz/leagues"):
        raise ReleaseError("Same-origin API routing failed its smoke check.")


def record_success(manifest, version):
    deployed = {**manifest, "deployedAt": datetime.now(timezone.utc).isoformat(), "lightsailVersion": version}
    with tempfile.TemporaryDirectory() as temporary:
        path = Path(temporary) / "latest.json"
        write_json(path, deployed)
        aws("s3", "cp", path, f"s3://{required('S3_BUCKET')}/_releases/latest.json", "--cache-control", "no-store",
            "--only-show-errors", json_result=False)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with Path(summary).open("a") as stream:
            stream.write(f"### Released `{manifest['commit']}`\n\n- Image: `{manifest['image']}`\n- Digest: `{manifest['imageDigest']}`\n- Lightsail version: {version}\n- Frontend: {required('SITE_URL')}\n")
    print(f"Released {manifest['commit']}; backend and frontend checks passed.")


def activate_release(directory, manifest):
    validate_image(manifest)
    publish_daily_media(directory, manifest)
    version = activate_backend(manifest)
    publish_frontend(directory, manifest)
    record_success(manifest, version)


def deploy(directory):
    commit = commit_sha(required("RELEASE_COMMIT"))
    root = https_root(required("SITE_URL"))
    # Infrastructure setup is deliberately separate from application releases.
    if not http_json(f"{root}/v0/quiz/leagues"):
        raise ReleaseError("Configure the CloudFront API origin before the first application release.")
    service = current_service()
    if not service.get("privateRegistryAccess", {}).get("ecrImagePullerRole", {}).get("isActive"):
        raise ReleaseError("Enable private ECR access for the Lightsail service before deploying.")
    runtime_environment(((service.get("currentDeployment") or {}).get("containers", {}).get(
        (service.get("currentDeployment") or {}).get("publicEndpoint", {}).get("containerName", "app"), {}).get("environment", {})))
    previous = remote_json("_releases/latest.json")
    published = remote_json(f"_releases/{commit}/manifest.json")
    if published is not None:
        # A rerun uses the exact published frontend/image pair, not a rebuilt approximation.
        manifest = download_release(commit, directory)
    else:
        manifest = load_manifest(directory / "manifest.json", commit)
        details = image_details(commit)
        if details is None:
            raise ReleaseError("Publish the commit image to ECR before deployment.")
        manifest.update(image=required("IMAGE_URI"), imageDigest=details["imageDigest"])
        validate_image(manifest)
        write_json(directory / "manifest.json", manifest)
        extract_frontend(directory / "frontend.tar.gz", directory / "frontend", manifest)
        for name in ("frontend.tar.gz", "manifest.json"):
            aws("s3", "cp", directory / name, f"s3://{required('S3_BUCKET')}/_releases/{commit}/{name}",
                "--cache-control", "no-store", "--only-show-errors", json_result=False)
    try:
        activate_release(directory, manifest)
    except Exception as error:
        if previous and previous.get("commit") != commit:
            print("Release failed. Restoring the previous recorded backend/frontend release.")
            try:
                with tempfile.TemporaryDirectory() as temporary:
                    restored = download_release(commit_sha(previous["commit"]), Path(temporary))
                    activate_release(Path(temporary), restored)
            except Exception:
                raise ReleaseError("Release failed and automatic rollback also failed. Inspect Lightsail and run the rollback action.") from error
            raise ReleaseError("Release failed; the previous recorded release was restored.") from error
        raise


def rollback(directory):
    manifest = download_release(commit_sha(required("RELEASE_COMMIT")), directory)
    activate_release(directory, manifest)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("prepare", "image", "deploy", "rollback"))
    parser.add_argument("--directory", type=Path, default=Path("release"))
    args = parser.parse_args()
    try:
        {"prepare": lambda: prepare(args.directory), "image": image_output,
         "deploy": lambda: deploy(args.directory), "rollback": lambda: rollback(args.directory)}[args.command]()
    except (ReleaseError, ValueError, KeyError, OSError, tarfile.TarError) as error:
        print(f"Release failed: {error}", file=sys.stderr)
        raise SystemExit(1)


if __name__ == "__main__":
    main()

"""Small path-style S3 client for seed/bootstrap jobs; Python standard library only."""
import datetime as dt
import hashlib
import hmac
import os
import time
import urllib.error
import urllib.parse
import urllib.request


class S3:
    def __init__(self, endpoint, access_key, secret_key, region="auto"):
        self.endpoint = endpoint.rstrip("/")
        self.access_key = access_key
        self.secret_key = secret_key
        self.region = region

    def request(self, method, bucket, key="", data=b"", content_type=None, query=""):
        path = "/" + urllib.parse.quote(bucket, safe="")
        if key:
            path += "/" + urllib.parse.quote(key, safe="/-_.~")
        now = dt.datetime.now(dt.timezone.utc)
        stamp, day = now.strftime("%Y%m%dT%H%M%SZ"), now.strftime("%Y%m%d")
        host = urllib.parse.urlsplit(self.endpoint).netloc
        payload_hash = hashlib.sha256(data).hexdigest()
        headers = {"host": host, "x-amz-content-sha256": payload_hash, "x-amz-date": stamp}
        signed = ";".join(sorted(headers))
        canonical_headers = "".join(k + ":" + headers[k] + "\n" for k in sorted(headers))
        canonical = "\n".join([method, path, query, canonical_headers, signed, payload_hash])
        scope = f"{day}/{self.region}/s3/aws4_request"
        to_sign = "\n".join(["AWS4-HMAC-SHA256", stamp, scope, hashlib.sha256(canonical.encode()).hexdigest()])
        signing_key = ("AWS4" + self.secret_key).encode()
        for value in (day, self.region, "s3", "aws4_request"):
            signing_key = hmac.new(signing_key, value.encode(), hashlib.sha256).digest()
        signature = hmac.new(signing_key, to_sign.encode(), hashlib.sha256).hexdigest()
        headers["Authorization"] = f"AWS4-HMAC-SHA256 Credential={self.access_key}/{scope}, SignedHeaders={signed}, Signature={signature}"
        if content_type:
            headers["Content-Type"] = content_type
        url = self.endpoint + path + ("?" + query if query else "")
        request = urllib.request.Request(url, data=data if method in ("PUT", "POST") else None, headers=headers, method=method)
        with urllib.request.urlopen(request, timeout=45) as response:
            return response.read(), dict(response.headers)

    def ensure_private_bucket(self, bucket):
        try:
            self.request("PUT", bucket)
        except urllib.error.HTTPError as error:
            body = error.read()
            if error.code != 409 or b"BucketAlreadyOwnedByYou" not in body:
                raise RuntimeError(f"Cannot create bucket: HTTP {error.code}") from error
        # A new bucket is private by default; remove any legacy public policy too.
        self.request("DELETE", bucket, query="policy=")


def main():
    client = S3(os.getenv("APP_STORAGE_ENDPOINT", "http://minio:9000"),
                os.environ["APP_STORAGE_ACCESS_KEY"], os.environ["APP_STORAGE_SECRET_KEY"],
                os.getenv("APP_STORAGE_REGION", "auto"))
    for attempt in range(30):
        try:
            client.ensure_private_bucket(os.environ["APP_STORAGE_BUCKET"])
            print("Storage bucket is ready and private.")
            return
        except (OSError, RuntimeError):
            if attempt == 29:
                raise
            time.sleep(2)


if __name__ == "__main__":
    main()

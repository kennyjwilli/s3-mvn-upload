# s3-mvn-upload

Uploads a jar file to a S3 Maven repo

## Usage

### Execute

```clojure
{:s3-deploy {:extra-deps {s3-mvn-upload/s3-mvn-upload {:mvn/version "RELEASE"}}
             :exec-fn    s3-mvn-upload.core/deploy
             :exec-args  {:artifact   "dev-local-0.9.172.jar"
                          :repository "s3://my-bucket/releases"}}}
```

### Main

```shell script
clojure -Sdeps '{:deps {s3-mvn-upload {:mvn/version "1.0.13"}}}' -M -m s3-mvn-upload.core dev-local-0.9.172.jar s3://my-bucket/releases
```

## Development

- Jar: clojure -T:build jar
- Deploy: clojure -T:build deploy

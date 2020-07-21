# s3-mvn-upload

Just uploads a jar file to a S3 Maven repo. 

## Prerequisites

The below CLI executables must be available. 

- md5sum
- aws

## Usage 

```shell script
clojure -Sdeps '{:deps {s3-mvn-upload {:mvn/version "0.1.0"}}}' -m s3-mvn-upload.core com.datomic/dev-local 0.9.172 dev-local-0.9.172.jar s3://my-bucket/releases
```

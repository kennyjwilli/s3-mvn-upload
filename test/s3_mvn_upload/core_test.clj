(ns s3-mvn-upload.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [s3-mvn-upload.core :as s3mu]))

(deftest md5sum
  (is (= "781e5e245d69b566979b86e28d23f2c7"
         (s3mu/md5sum "test/md5sum-example.txt"))))

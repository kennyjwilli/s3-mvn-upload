(ns build
  "sorted-multiset's build script.
  clojure -T:build ci
  clojure -T:build deploy
  Run tests via:
  clojure -X:test
  For more information, run:
  clojure -A:deps -T:build help/doc"
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as bb]))

(def lib 's3-mvn-upload/s3-mvn-upload)
(def version (format "1.0.%s" (b/git-count-revs nil)))

(defn jar "Build lib jar." [opts]
  (-> (assoc opts :lib lib :version version)
    (bb/clean)
    (bb/jar))
  opts)

(defn deploy "Deploy the JAR to Clojars." [opts]
  (-> opts
    (assoc :lib lib :version version)
    (bb/deploy)))

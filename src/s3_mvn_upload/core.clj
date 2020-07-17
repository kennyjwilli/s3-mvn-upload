(ns s3-mvn-upload.core
  (:require
    [clojure.string :as str]
    [clojure.java.shell :as shell]))

(defn print-err
  [x]
  (binding [*out* *err*]
    (println x)))

(defn canonicalize-name
  [artifact-name]
  (if (qualified-symbol? artifact-name)
    artifact-name
    (symbol artifact-name artifact-name)))

(defn s3-key
  [artifact-name version]
  (let [n (canonicalize-name artifact-name)]
    (str/join "/"
              (concat (str/split (namespace n) #"\.")
                      [(name n)
                       version
                       (str (name n) "-" version ".jar")]))))

(defn md5sum
  [path]
  (try
    (let [{:keys [exit out err]} (shell/sh "md5sum" path)]
      (print-err err)
      (when (= exit 0)
        (first (str/split out #" "))))
    (catch Exception ex
      (print-err (.getMessage ex))
      nil)))

(defn upload!
  [path s3-uri]
  (let [{:keys [exit out err]} (shell/sh "aws" "s3" "cp" "--no-progress" path s3-uri)]
    (println out)
    (print-err err)
    (= exit 0)))

(defn run
  [[artifact-name-str version jar-file uri-start]]
  (assert artifact-name-str "missing artifact name")
  (assert version "missing version")
  (assert jar-file "missing jar file")

  (let [md5-path (str jar-file ".md5")
        s3-key (s3-key (clojure.edn/read-string artifact-name-str) version)
        urif #(str uri-start "/" %)]
    (spit md5-path (md5sum jar-file))
    (upload! md5-path (urif (str s3-key ".md5")))
    (upload! jar-file (urif s3-key))))

(defn -main
  [& args]
  (run args)
  (shutdown-agents))

;; artifact version jarfile s3://my-bucket/releases
(ns s3-mvn-upload.core
  (:require
    [clojure.string :as str]
    [clojure.java.io :as io]
    [clojure.java.shell :as shell])
  (:import (java.util.jar JarFile JarEntry JarInputStream)
           (com.amazonaws.services.s3 AmazonS3 AmazonS3ClientBuilder)
           (java.io IOException)
           (java.nio.file Paths Files)
           (java.security MessageDigest)
           (com.amazonaws.services.s3.model PutObjectRequest ObjectMetadata)
           (java.net URI)))

(set! *warn-on-reflection* true)

(defn print-err
  [x]
  (binding [*out* *err*]
    (println x)))

(defn canonicalize-name
  [artifact-name]
  (if (qualified-symbol? artifact-name)
    artifact-name
    (symbol artifact-name artifact-name)))

(defn jar-name
  [x]
  (first (str/split x #"\.jar")))

(defn s3-key
  [artifact-name version extension]
  (let [n (canonicalize-name artifact-name)]
    (str/join "/"
              (concat (str/split (namespace n) #"\.")
                      [(name n)
                       version
                       (str (name n) "-" version extension)]))))

(defn md5sum
  [path]
  (let [uri (.toURI (io/file path))]
    (-> (MessageDigest/getInstance "MD5")
        (doto (.update (Files/readAllBytes (Paths/get uri))))
        .digest
        (->> (BigInteger. 1))
        (.toString 16))))

(defn find-pom-contents
  "Find path of pom file in jar file, or nil if it doesn't exist"
  [^JarFile jar-file]
  (try
    (loop [[^JarEntry entry & entries] (enumeration-seq (.entries jar-file))]
      (when entry
        (let [name (.getName entry)]
          (if (and (str/starts-with? name "META-INF/")
                   (str/ends-with? name "pom.xml"))
            (with-open [jis (.getInputStream jar-file entry)]
              (slurp jis))
            (recur entries)))))
    (catch IOException _t nil)))

(comment
  (find-pom (JarFile. (io/file "s3-mvn-upload.jar"))))
  (find-pom (JarFile. (io/file "s3-mvn-upload.jar"))))

(def standard-s3-client
  (delay
    (.build (AmazonS3ClientBuilder/standard))))

(defn upload!
  [path s3-uri]
  (let [uri (URI. s3-uri)]
    (.putObject ^AmazonS3 @standard-s3-client
                (PutObjectRequest.
                  (.getHost uri)
                  (subs (.getPath uri) 1)
                  ;; TODO: I can use `io/input-stream` here
                  ;; it allow to upload files without create a file in disk
                  ;; ATM Generates  this warn:
                  ;; WARNING: No content length specified for stream data.  Stream contents will be buffered in memory and could result in out of memory errors.
                  (io/file path)
                  #_(ObjectMetadata.)))))

(defn run
  [[artifact-name-str version jar-file uri-start]]
  (assert artifact-name-str "missing artifact name")
  (assert version "missing version")
  (assert jar-file "missing jar file")

  (let [md5-path (str jar-file ".md5")
        pom-path (str (jar-name jar-file) ".pom")
        pom-md5-path (str (jar-name jar-file) ".pom.md5")
        artifact-name (clojure.edn/read-string artifact-name-str)
        urif #(str uri-start "/" %)
        pom-contents (find-pom-contents (JarFile. (io/file jar-file)))]
    (assert pom-contents "Jar is missing a pom.xml file.")

    (spit md5-path (md5sum jar-file))
    (spit pom-path pom-contents)
    (spit pom-md5-path (md5sum pom-path))
    (upload! md5-path (urif (s3-key artifact-name version ".jar.md5")))
    (upload! pom-path (urif (s3-key artifact-name version ".pom")))
    (upload! pom-md5-path (urif (s3-key artifact-name version ".pom.md5")))
    (upload! jar-file (urif (s3-key artifact-name version ".jar")))))

(defn -main
  [& args]
  (run args)
  (shutdown-agents))

;; artifact version jarfile s3://my-bucket/releases
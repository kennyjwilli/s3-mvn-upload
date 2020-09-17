(ns s3-mvn-upload.core
  (:require
    [clojure.string :as str]
    [clojure.java.io :as io])
  (:import (java.util.jar JarFile JarEntry)
           (com.amazonaws.services.s3 AmazonS3 AmazonS3ClientBuilder)
           (java.io IOException ByteArrayInputStream InputStream)
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
  (find-pom-contents (JarFile. (io/file "s3-mvn-upload.jar"))))

(def standard-s3-client
  (delay
    (.build (AmazonS3ClientBuilder/standard))))

(defn ^InputStream str-is
  [^String s]
  (ByteArrayInputStream. (.getBytes s)))

(defn upload!
  ([c s3-uri ^String string-content]
   (with-open [is (str-is string-content)]
     (upload! c s3-uri is (alength (.getBytes string-content)))))
  ([c s3-uri ^InputStream input length]
   (let [uri (URI. s3-uri)]
     (.putObject ^AmazonS3 c
                 (PutObjectRequest.
                   (.getHost uri)
                   (subs (.getPath uri) 1)
                   input
                   (doto (ObjectMetadata.)
                     (.setContentLength length)))))))

(defn run
  [[artifact-name-str version jar-path uri-start]]
  (assert artifact-name-str "missing artifact name")
  (assert version "missing version")
  (assert jar-path "missing jar file")

  (let [artifact-name (clojure.edn/read-string artifact-name-str)
        urif #(str uri-start "/" %)
        jar-file (io/file jar-path)
        pom-contents (find-pom-contents (JarFile. jar-file))
        pom-md5 (md5sum jar-path)
        jar-md5 (md5sum jar-path)
        c @standard-s3-client]
    (assert pom-contents "Jar is missing a pom.xml file.")

    (upload! c (urif (s3-key artifact-name version ".jar.md5")) jar-md5)
    (upload! c (urif (s3-key artifact-name version ".pom")) pom-contents)
    (upload! c (urif (s3-key artifact-name version ".pom.md5")) pom-md5)
    (with-open [is (io/input-stream jar-file)]
      (upload! c (urif (s3-key artifact-name version ".jar")) is (.length jar-file)))))

(defn -main
  [& args]
  (run args)
  (shutdown-agents))

;; artifact version jarfile s3://my-bucket/releases
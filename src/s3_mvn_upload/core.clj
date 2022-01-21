(ns s3-mvn-upload.core
  (:require
    [clojure.string :as str]
    [clojure.xml :as xml]
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

(defn md5sum-bytes
  [^bytes bytes]
  (-> (MessageDigest/getInstance "MD5")
    (doto (.update bytes))
    .digest
    (->> (BigInteger. 1))
    (.toString 16)))

(defn md5sum-file
  [path]
  (md5sum-bytes (-> path io/file .toURI Paths/get Files/readAllBytes)))

(defn ^java.io.InputStream find-pom-input-stream
  "Find path of pom file in jar file, or nil if it doesn't exist"
  [^JarFile jar-file]
  (try
    (loop [[^JarEntry entry & entries] (enumeration-seq (.entries jar-file))]
      (when entry
        (let [name (.getName entry)]
          (if (and (str/starts-with? name "META-INF/")
                (str/ends-with? name "pom.xml"))
            (.getInputStream jar-file entry)
            (recur entries)))))
    (catch IOException _t nil)))

(defn find-pom-contents
  "Find path of pom file in jar file, or nil if it doesn't exist"
  [^JarFile jar-file]
  (with-open [is (find-pom-input-stream jar-file)]
    (slurp is)))

(comment
  (find-pom-contents (JarFile. (io/file "s3-mvn-upload.jar"))))

(defn find-artifact-coords
  [xml-parsable]
  (let [pom-data (xml/parse xml-parsable)
        {:keys [groupId artifactId version]} (into {} (map (juxt :tag identity)) (:content pom-data))
        group-id (-> groupId :content first)
        artifact-id (-> artifactId :content first)
        version (-> version :content first)]
    {:group-id    group-id
     :artifact-id artifact-id
     :version     version}))

(comment
  (def pom-is (find-pom-input-stream (JarFile. "dev-local-1.0.242.jar")))
  (find-artifact-coords pom-is)
  (def pom-data (clojure.xml/parse pom-is)))

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
     (with-open [input input]
       (.putObject ^AmazonS3 c
         (PutObjectRequest.
           (.getHost uri)
           (subs (.getPath uri) 1)
           input
           (doto (ObjectMetadata.)
             (.setContentLength length))))))))

(defn deploy-details
  [{:keys [artifact repository]}]
  (let [urif #(str repository "/" %)
        jar-file (io/file artifact)
        pom-jar-file (JarFile. jar-file)
        pom-contents (find-pom-contents pom-jar-file)
        {:keys [group-id artifact-id version]} (find-artifact-coords (find-pom-input-stream pom-jar-file))
        artifact-coord-sym (symbol (or group-id artifact-id) artifact-id)
        pom-md5 (md5sum-bytes (.getBytes ^String pom-contents))
        jar-md5 (md5sum-file jar-file)]
    {:uploads [{:key  (urif (s3-key artifact-coord-sym version ".jar.md5"))
                :data jar-md5}
               {:key  (urif (s3-key artifact-coord-sym version ".pom"))
                :data pom-contents}
               {:key  (urif (s3-key artifact-coord-sym version ".pom.md5"))
                :data pom-md5}
               {:key  (urif (s3-key artifact-coord-sym version ".pom.md5"))
                :data pom-md5}
               {:key    (urif (s3-key artifact-coord-sym version ".jar"))
                :data   (io/input-stream jar-file)
                :length (.length jar-file)}]}))

(comment
  (deploy-details {:artifact   "dev-local-1.0.242.jar"
                   :repository "s3://example/releases"}))

(defn deploy
  [opts]
  (let [{:keys [uploads]} (deploy-details opts)
        c @standard-s3-client]
    (doseq [{:keys [key data length]} uploads]
      (if length
        (upload! c key data length)
        (upload! c key data)))
    (shutdown-agents)
    nil))

(defn -main
  [& args]
  (deploy {:artifact (first args) :repository (second args)}))

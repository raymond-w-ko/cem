(ns build
  (:require [clojure.tools.build.api :as b]))

(def src-dirs ["src/jvm"])
(def lib 'cem/cem)
(def version (format "0.1.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def basis (delay (b/create-basis {:project "deps.edn"})))
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn compile-java [& _]
  (clean nil)
  (b/javac {:basis @basis
            :src-dirs src-dirs
            :class-dir "target/classes"
            :javac-opts ["--release" "11"
                         "-proc:full"]}))

(defn jar [_]
  (clean nil)
  (compile-java)
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis @basis
                :src-dirs src-dirs})
  (b/copy-dir {:src-dirs src-dirs
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

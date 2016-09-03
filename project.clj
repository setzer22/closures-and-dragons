(defproject ui-test "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [spyscope "0.1.5"]
                 [com.rpl/specter "0.12.0"]
                 [org.clojure/core.async "0.2.385"]
                 [net.async/async "0.1.0"]
                 [net.mikera/vectorz-clj "0.45.0"]
                 [net.mikera/core.matrix "0.54.0"]]
  :jvm-opts ["-Dsun.java2d.opengl=true"]
  :java-source-paths ["src/main/java"]
  :main ^:skip-aot ui-test.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})

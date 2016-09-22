(defproject net.thegeez/clj-board "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://thegeez.net"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha10"]
                 [org.clojure/core.async "0.2.374"]

                 [com.stuartsierra/component "0.2.1"]

                 [io.pedestal/pedestal.service "0.5.0"]
                 [io.pedestal/pedestal.jetty "0.5.0"]
                 [org.clojure/core.async "0.2.374"]

                 [ch.qos.logback/logback-classic "1.1.2" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.7"]
                 [org.slf4j/jcl-over-slf4j "1.7.7"]
                 [org.slf4j/log4j-over-slf4j "1.7.7"]

                 [org.clojure/java.jdbc "0.3.0"]
                 [postgresql/postgresql "8.4-702.jdbc4"]
                 [com.mchange/c3p0 "0.9.2.1"]
                 ;; temp in prod
                 [org.apache.derby/derby "10.8.1.2"]
                 [enlive "1.1.5"]

                 [org.webjars/bootstrap "3.3.4"]
                 [org.webjars/jquery "1.11.1"]
                 [org.webjars/font-awesome "4.6.3"]
                ;; [org.webjars.npm/react-dnd-html5-backend "2.0.0"]
                ;; [org.webjars.npm/react-dnd "2.1.4"]

                 [clj-jwt "0.1.1"]

                 [clj-time "0.11.0"]]

  :resource-paths ["config", "resources"]

  :min-lein-version "2.0.0"

  :source-paths ["src/clj"]
  :profiles {:dev {:source-paths ["dev/clj"]
                   :main user
                   :dependencies [[ns-tracker "0.2.2"]
                                  [reloaded.repl "0.2.1"]
                                  [org.apache.derby/derby "10.8.1.2"]
                                  [kerodon "0.7.0"]
                                  [peridot "0.3.1" :exclusions [clj-time]]


                                  [org.clojure/clojurescript "1.9.89"]
                                  [figwheel-sidecar "0.5.4-6"]
                                  [com.cemerick/piggieback "0.2.1"]
                                  [re-frame "0.8.0-alpha11"]
                                  [secretary "1.2.3"]
                                  [cljs-ajax "0.5.4"]]}
             :uberjar {:main net.thegeez.clj-board.main
                       :aot [net.thegeez.clj-board.main]
                       :uberjar-name "clj-board-prod-standalone.jar"}})

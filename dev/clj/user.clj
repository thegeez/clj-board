(ns user
  (:require [reloaded.repl :refer [set-init! system init start stop go reset]]
            [figwheel-sidecar.repl-api :as ra]
            [cljs.build.api]))

(set-init! #(do (require 'dev-system) ((resolve 'dev-system/dev-system))))

(defn cljs-start []
  (ra/start-figwheel!
   {:figwheel-options {:css-dirs ["resources/public/css"]}
    :build-ids ["dev"]
    :all-builds
    [{:id "dev"
      :figwheel {:on-jsload "net.thegeez.clj-board.dev/reload"}
      :source-paths ["src/cljs" "dev/cljs"]
      :compiler {:main "net.thegeez.clj-board.core"
                 :asset-path "/js/out"
                 :output-to "resources/public/js/app.js"
                 :output-dir "resources/public/js/out"
                 ;; for prod:
                 ;;:externs [""]
                 :verbose true}}]}))

(defn cljs-repl []
  (ra/cljs-repl))

(defn cljs-compile []
  (println "Compiling app.js")
  (cljs.build.api/build "src/cljs"
                        {:optimizations :advanced
                         :output-to "resources/public/js/app.js"
                         :output-dir "resources/public/js/out"
                         :externs ["externs/react-dnd-externs.js"]})
  (println "Compiling done!"))


(defn all []
  (go)
  (cljs-start)
  (cljs-repl))

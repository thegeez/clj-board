(ns net.thegeez.clj-board.dev
  (:require [net.thegeez.clj-board.core :as core]))

(enable-console-print!)

(defn reload []
  (println "reloading")
  (core/main))

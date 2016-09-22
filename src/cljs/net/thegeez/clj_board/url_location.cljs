(ns net.thegeez.clj-board.url-location
  (:require [goog.events]
            [goog.history.EventType]
            [goog.history.Html5History]
            [goog.Uri]
            [secretary.core :as secretary]))

(defn load []
  (defonce h
    (let [h (goog.history.Html5History.)]
      (goog.events/listen h
                          goog.history.EventType/NAVIGATE
                          (fn [e]
                            (let [token (.getPath (goog.Uri. js/document.location.href))]
                              (secretary/dispatch! token))))
      (.setUseFragment h false)
      (let [href js/document.location.href
            path (.getPath (goog.Uri. js/document.location.href))
            url (-> href
                    (subs 0 (- (count href)
                               (count path))))]
        (.setPathPrefix h url))
      (.setEnabled h true)
      h)))

(defn link [e]
  (.preventDefault e)
  (let [loc (-> (goog.Uri. (.. e -target -href))
                (.getPath))]
    (.setToken h loc)))

(defn goto [url]
  (let [token (.getPath (goog.Uri. js/document.location.href))]
    (when-not (= token url)
      (.setToken h url))))

(defn logout []
  (let [href js/document.location.href
        path (.getPath (goog.Uri. js/document.location.href))
        url (-> href
                (subs 0 (- (count href)
                           (count path))))]
    (set! js/window.location (str url "/sign-in"))))

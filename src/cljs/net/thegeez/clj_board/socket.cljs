(ns net.thegeez.clj-board.socket
  (:require [re-frame.core :as rf]
            [re-frame.db]
            [ajax.core :as ajax]
            [goog.Uri]
            [cljs.reader :as reader]))

(defmulti handle-data
  (fn [data]
    (first data)))

(defmethod handle-data :default
  [data]
  (println "handle-data" data))

(defn send-msg [msg]) ;; hacky

(defonce retries (atom 0))
(def show-error (atom false))

(def random-id (random-uuid))

(defn make-ws-connection []
  (let [href js/document.location.href
        uri (goog.Uri. js/document.location.href)
        root (str (.getDomain uri) (when (.hasPort uri)
                                     (str ":" (.getPort uri))))
        jwt (.getItem js/localStorage "authToken")
        w (js/WebSocket. (str "ws:" root "/ws?token=" jwt "&random=" random-id))
        _ (swap! retries inc)
        heart-beat (fn heart-beat []
                     (send-msg [:heart-beat])
                     (.setTimeout js/window
                                  heart-beat
                                  2000))
        reconnect-ws (fn []
                       (set! send-msg (fn [msg]))
                       (if (< @retries 10)
                         (.setTimeout js/window
                                      (fn []
                                        (println "close w")
                                        (.close w)
                                        (make-ws-connection))
                                      3000)
                         (do (println "WS Retries exhausted")
                             (when-not  @show-error
                               (reset! show-error true)
                               (js/alert "WebSocket connection failure, please reload the page")))))]
    (set! (.-onmessage w) (fn [e]
                            ;;(.log js/console (str "got: " (.-data e) ))
                            (println "WS: message " (.-data e))
                            (handle-data (reader/read-string (.-data e)))))
    (set! (.-onopen w) (fn [e]
                         (set! send-msg (fn [msg]
                                          (.send w (pr-str msg))))
                         (heart-beat)
                         (println "WS open: " {:msg "conn open"}
                                  "Looking at: "
                                  (let [[_ _ board-id] (:route @re-frame.db/app-db)]
                                    board-id))
                         (let [[_ _ board-id] (:route @re-frame.db/app-db)]
                           (when board-id
                             (send-msg [:join-board board-id])))))
    (set! (.-onclose w) (fn [e]
                          (println "WS close: " {:msg "conn close"})
                          (reconnect-ws)))
    (set! (.-onerror w) (fn [e]
                          (println "WS error: " {:msg "conn error"} e)
                          (reconnect-ws)))
    (def w w)
    w))

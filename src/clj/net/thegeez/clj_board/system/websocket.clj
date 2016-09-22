(ns net.thegeez.clj-board.system.websocket
  (:require [clojure.core.async :as async]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [com.stuartsierra.component :as component]
            [io.pedestal.log :as log]
            [io.pedestal.http.jetty.websockets :as ws]
            [net.thegeez.clj-board.jwt :as jwt])
  (:import (org.eclipse.jetty.websocket.api
            WebSocketListener
            WebSocketConnectionListener)))

(defn get-token-connection-id [req]
  (let [request-uri (str (.getRequestURI req))
        preamble "?token="
        second "&random="
        start (string/index-of request-uri preamble)
        end (string/index-of request-uri second)
        token (subs request-uri (+ start (count preamble)) end)
        connection-id (subs request-uri (+ end (count second)) (count request-uri))]
    [token connection-id]))

;; pedestal ws support is meh
(def ws-clients (atom {}))

(defn new-ws-client
  [ws-session send-ch]
  (let [[token connection-id] (get-token-connection-id ws-session)]
    (if-let [user (jwt/jwt-decode-to-user token)]
      (do (log/info :ws-session token)
          ;; token can be mapped to multiple clients, when having mulitple taps open
          (swap! ws-clients assoc-in [token connection-id] {:ws-session ws-session
                                                            :send-ch send-ch}))
        (do
          (.close ws-session 401 "No valid token")))))

(defn send-ws [token msg]
  (when-let [conns (get @ws-clients token)]
    (doseq [[connection-id conn] conns]
      (let [{:keys [ws-session send-ch]} conn]
        (try (async/put! send-ch (pr-str msg))
             (catch Exception _ nil)
             (catch AssertionError _
               ;; assert max puts on send-ch exceeded
               ;; assume the channel is for a disconnected client
               (async/close! send-ch)
               nil))))))

(defn websocket-listener [handler-fn database listener reply broadcast remover]
  (let [handler (handler-fn database listener reply broadcast remover send-ws)]
    (fn [req response]
      (let [[token connection-id] (get-token-connection-id req)]
        (reify
          WebSocketConnectionListener
          (onWebSocketConnect [this ws-session]
            ((ws/start-ws-connection new-ws-client) ws-session))
          (onWebSocketClose [this status-code reason]
            (log/info :msg "WS Closed:" :reason reason)
            (remover token connection-id))
          (onWebSocketError [this cause]
            (log/error :msg "WS Error happened" :exception cause))

          WebSocketListener
          (onWebSocketText [this msg]
            (log/info :msg msg)
            (let [msg (read-string msg)]
              (handler token connection-id msg)))
          (onWebSocketBinary [this payload offset length]
            (log/info :msg "Binary Message!" :bytes payload)))))))

(def ws-paths {"/ws" {}})

(def old-ws-servlet (atom nil))

(defrecord WebsocketComponent [handler-fn database messenger]
  component/Lifecycle
  (start [component]
    (log/info :msg "Starting websocket component")
    (let [running (atom true)]
      ;; super hacky, next pedestal version has better support for custom listener
      (alter-var-root #'ws/ws-servlet (fn [old]
                                        (reset! old-ws-servlet old)
                                        (fn [_]
                                          (old (websocket-listener
                                                handler-fn
                                                (:spec database)
                                                (:listener messenger)
                                                (:reply messenger)
                                                (:broadcast messenger)
                                                (:remover messenger))))))
      (assoc component
             :ws-paths ws-paths
             :running running)))

  (stop [component]
    (log/info :msg "Stopping websocket component")
    (alter-var-root #'ws/ws-servlet (fn [old]
                                      @old-ws-servlet))
    (reset! (:running component) false)
    component))

(defn websocket-component [handler-fn]
  (map->WebsocketComponent {:handler-fn handler-fn}))

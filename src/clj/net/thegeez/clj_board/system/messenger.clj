(ns net.thegeez.clj-board.system.messenger
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [io.pedestal.log :as log]
            [net.thegeez.clj-board.system.websocket :as websocket]))

;; in memory version single node/instance ony
;; user is identified with token, connection-id is tab/window, user can have more than one window open

(defn connected-user-ids [tokens+conns+user]
  (->> tokens+conns+user
       (mapcat (fn [[token conn-id+user]]
                 (map (fn [[conn-id user]]
                        (:id user))
                      conn-id+user)))))

(defrecord MessengerComponent []
  component/Lifecycle
  (start [component]
    (log/info :msg "Starting messenger component")
    (let [subs (atom {})]

      (assoc component
             :listener (fn [board-slug token connection-id user]
                         (let [res (swap! subs assoc-in [board-slug token connection-id]
                                          user)
                               users (connected-user-ids (get res board-slug))]
                           (doseq [[token conn-id+user] (get res board-slug)]
                             (websocket/send-ws token [:users board-slug users]))))
             :reply (fn [token event]
                      (when-let [conn-id+user (get (reduce merge (vals @subs)) token)]
                        (websocket/send-ws token event)))
             :broadcast (fn [board-slug event]
                          (doseq [[token conn-id+user] (get @subs board-slug)]
                            (websocket/send-ws token event)))
             :remover (fn [token connection-id]
                        (let [in-boards (for [[board-slug token+data] @subs
                                              :when (contains? token+data token)]
                                          board-slug)]
                          (doseq [board-slug in-boards]
                            (let [res (swap! subs update-in [board-slug]
                                             (fn [tokens+conns+user]
                                               ;; remove token if last connection for the token
                                               (if (= (count (get tokens+conns+user token)) 1)
                                                 (dissoc tokens+conns+user token)
                                                 (update-in tokens+conns+user [token]
                                                            dissoc connection-id))))
                                  users (connected-user-ids (get res board-slug))]
                              (doseq [[token conn-id+user] (get res board-slug)]
                                (websocket/send-ws token [:users board-slug users])))))))))

  (stop [component]
    (log/info :msg "Stopping messenger component")
    component))

(defn messenger-component []
  (map->MessengerComponent {}))

(ns net.thegeez.clj-board.database.fixtures
  (:require [com.stuartsierra.component :as component]
            [io.pedestal.log :as log]
            [clojure.java.jdbc :as jdbc]
            [net.thegeez.clj-board.boards :as boards]))

(defn create-demo-board [db user-id]
  (let [now (.getTime (java.util.Date.))
        two-user-id (:id (first (jdbc/query db ["select id from users where username = ?" "User Two"])))
        demo-board-id (first
                       (map (fn [r]
                              (or (:1 r) (:id r)))
                            (jdbc/insert! db :boards
                                          {:name "Demo board"
                                           :slug "demo-board"
                                           :user_id user-id
                                           :created_at now
                                           :updated_at now})))
        _ (jdbc/insert! db :user_board
                        {:user_id user-id
                         :board_id demo-board-id}
                        {:user_id two-user-id
                         :board_id demo-board-id})
        [l1 l2 l3] (map (fn [r]
                          (or (:1 r)
                              (:id r)))
                        (jdbc/insert! db :lists
                                      {:board_id demo-board-id
                                       :name "Ideas"
                                       :position 1024}
                                      {:board_id demo-board-id
                                       :name "Active"
                                       :position 2048}
                                      {:board_id demo-board-id
                                       :name "Done"
                                       :position 3072}
                        ))
        [c1 c2 c3 c4 c5 c6 c7 c8]
        (map (fn [r]
               (or (:1 r)
                   (:id r)))
             (jdbc/insert! db :cards
                           {:list_id l1
                            :name "Proper signup"
                            :position 1024}
                           {:list_id l1
                            :name "User avatars"
                            :position 2048}
                           {:list_id l2
                            :name "Deploy"
                            :position 1024}
                           {:list_id l3
                            :name "Drag&Drop"
                            :description "Using react-dnd"
                            :position 1024}
                           {:list_id l3
                            :name "Comments"
                            :position 2048}
                           {:list_id l3
                            :name "Member presence"
                            :position 3072}
                           {:list_id l3
                            :name "Add tags"
                            :position 4096}
                           {:list_id l3
                            :name "Websockets"
                            :description "Should be possible with pedestal"
                            :position 5120}))]
    (boards/add-comment db c1 user-id "Replaced by demo signin")
    (boards/toggle-tag db c1 1 true)
    (boards/toggle-card-member db c1 user-id true)

    (boards/add-comment db c2 user-id "Too much work")
    (boards/toggle-tag db c2 1 true)
    (boards/toggle-card-member db c2 user-id true)
    (boards/toggle-card-member db c2 two-user-id true)

    (boards/toggle-tag db c3 2 true)
    (boards/toggle-tag db c3 5 true)
    (boards/toggle-card-member db c3 user-id true)
    (boards/toggle-card-member db c3 two-user-id true)

    (boards/add-comment db c4 user-id "Lots of hoops to jump through")
    (boards/toggle-tag db c4 3 true)
    (boards/toggle-card-member db c4 user-id true)
    (boards/toggle-card-member db c4 two-user-id true)

    (boards/toggle-tag db c5 3 true)
    (boards/toggle-tag db c5 6 true)
    (boards/toggle-card-member db c5 user-id true)

    (boards/add-comment db c6 user-id "Now shows who is online or offline")
    (boards/toggle-tag db c6 3 true)
    (boards/toggle-tag db c6 6 true)
    (boards/toggle-card-member db c6 user-id true)

    (boards/toggle-tag db c7 3 true)
    (boards/toggle-card-member db c7 user-id true)

    (boards/add-comment db c8 user-id "Works most of the time")
    (boards/toggle-tag db c8 4 true)
    (boards/toggle-tag db c8 6 true)
    (boards/toggle-card-member db c8 user-id true)))

(defn insert-fixtures! [db]
  (let [now (.getTime (java.util.Date.))]
    (jdbc/insert! db :users
                  {:username "Demo User"
                   :email "demo-user@clj-board.thegeez.net"
                   :created_at now
                   :updated_at now}
                  {:username "User Two"
                   :email "user-two@clj-board.thegeez.net"
                   :created_at now
                   :updated_at now}
                  {:username "Three User"
                   :email "three-user@clj-board.thegeez.net"
                   :created_at now
                   :updated_at now})
    (let [[demo-user-id two-user-id] (map #(:id (first (jdbc/query db ["select id from users where username = ?" %]))) ["Demo User" "User Two" "Three User"])]
      (create-demo-board db demo-user-id))))

(defrecord Fixtures [database]
  component/Lifecycle
  (start [component]
    (log/info :msg "Starting fixture loader")
    (when-not (:loaded-fixtures component)
      (try
        (insert-fixtures! (:spec database))
        (catch Exception e
          (log/info :loading-fixtures-failed (.getMessage e)))))
    (assoc component :loaded-fixtures true))

  (stop [component]
    (log/info :msg "Stopping fixture loader")
    component))

(defn fixtures []
  (map->Fixtures {}))

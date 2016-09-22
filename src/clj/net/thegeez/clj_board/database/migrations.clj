(ns net.thegeez.clj-board.database.migrations
  (:require [io.pedestal.log :as log]
            [clojure.java.jdbc :as jdbc]
            [net.thegeez.clj-board.system.sql-database.migrator :as migrator]))

(defn serial-id [db]
  (if (.contains (:connection-uri db) "derby")
    [:id "INTEGER NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1)"]
    [:id :serial "PRIMARY KEY"]))

(def migrations
  [[1 migrator/version-migration]
   (let [table :users]
     [2 {:up (fn [db]
               (jdbc/db-do-commands
                db (jdbc/create-table-ddl
                    table
                    (serial-id db)
                    [:username "VARCHAR(256) UNIQUE"]
                    [:email "VARCHAR(256) UNIQUE"]
                    [:created_at "BIGINT"]
                    [:updated_at "BIGINT"])))
         :down (fn [db]
                 (jdbc/db-do-commands
                  db (jdbc/drop-table-ddl
                      table)))}])
   (let [table :boards]
     [3 {:up (fn [db]
               (jdbc/db-do-commands
                db (jdbc/create-table-ddl
                    table
                    (serial-id db)
                    [:name "VARCHAR(1024)"]
                    [:slug "VARCHAR(1024) UNIQUE"]
                    [:user_id "BIGINT"]

                    [:created_at "BIGINT"]
                    [:updated_at "BIGINT"])))
         :down (fn [db]
                 (jdbc/db-do-commands
                  db (jdbc/drop-table-ddl
                      table)))}])
   (let [table :user_board]
     [4 {:up (fn [db]
               (jdbc/db-do-commands
                db (jdbc/create-table-ddl
                    table
                    (serial-id db)
                    [:user_id "BIGINT"]
                    [:board_id "BIGINT"])))
         :down (fn [db]
                 (jdbc/db-do-commands
                  db (jdbc/drop-table-ddl
                      table)))}])
   (let [table :lists]
     [5 {:up (fn [db]
               (jdbc/db-do-commands
                db (jdbc/create-table-ddl
                    table
                    (serial-id db)
                    [:board_id "BIGINT"]
                    [:name "VARCHAR(256)"]
                    [:position "BIGINT"])))
         :down (fn [db]
                 (jdbc/db-do-commands
                  db (jdbc/drop-table-ddl
                      table)))}])
   (let [table :cards]
     [6 {:up (fn [db]
               (jdbc/db-do-commands
                db (jdbc/create-table-ddl
                    table
                    (serial-id db)
                    [:list_id "BIGINT"]
                    [:name "VARCHAR(256)"]
                    [:position "BIGINT"]
                    [:description "VARCHAR(2048)"]
                    ;; todo tags
                    )))
         :down (fn [db]
                 (jdbc/db-do-commands
                  db (jdbc/drop-table-ddl
                      table)))}])
   (let [table :comments]
     [7 {:up (fn [db]
               (jdbc/db-do-commands
                db (jdbc/create-table-ddl
                    table
                    (serial-id db)
                    [:user_id "BIGINT"]
                    [:card_id "BIGINT"]
                    [:text "VARCHAR(4096)"]
                    [:created_at "BIGINT"]
                    )))
         :down (fn [db]
                 (jdbc/db-do-commands
                  db (jdbc/drop-table-ddl
                      table)))}])
   (let [table :tags]
     [8 {:up (fn [db]
               (jdbc/db-do-commands
                db (jdbc/create-table-ddl
                    table
                    (serial-id db)
                    [:color "VARCHAR(7)"]
                    ))
               (jdbc/insert! db :tags
                             {:color "red"}
                             {:color "blue"}
                             {:color "green"}
                             {:color "yellow"}
                             {:color "orange"}
                             {:color "purple"}))
         :down (fn [db]
                 (jdbc/db-do-commands
                  db (jdbc/drop-table-ddl
                      table)))}])
   (let [table :cards_tags]
     [9 {:up (fn [db]
               (jdbc/db-do-commands
                db (jdbc/create-table-ddl
                    table
                    (serial-id db)
                    [:card_id "BIGINT"]
                    [:tag_id "BIGINT"]
                    )))
         :down (fn [db]
                 (jdbc/db-do-commands
                  db (jdbc/drop-table-ddl
                      table)))}])
   (let [table :cards_users]
     [10 {:up (fn [db]
                (jdbc/db-do-commands
                 db (jdbc/create-table-ddl
                     table
                     (serial-id db)
                     [:card_id "BIGINT"]
                     [:user_id "BIGINT"]
                     )))
          :down (fn [db]
                  (jdbc/db-do-commands
                   db (jdbc/drop-table-ddl
                       table)))}])])
